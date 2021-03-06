/*
 * Copyright 2020 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rs.ltt.jmap.mua.service;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.client.JmapRequest;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.client.api.MethodErrorResponseException;
import rs.ltt.jmap.client.session.Session;
import rs.ltt.jmap.common.Request;
import rs.ltt.jmap.common.entity.AddedItem;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.capability.CoreCapability;
import rs.ltt.jmap.common.entity.filter.Filter;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.common.method.MethodErrorResponse;
import rs.ltt.jmap.common.method.call.email.GetEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryChangesEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryEmailMethodCall;
import rs.ltt.jmap.common.method.call.thread.GetThreadMethodCall;
import rs.ltt.jmap.common.method.error.AnchorNotFoundMethodErrorResponse;
import rs.ltt.jmap.common.method.response.email.GetEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.QueryChangesEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.QueryEmailMethodResponse;
import rs.ltt.jmap.common.method.response.thread.GetThreadMethodResponse;
import rs.ltt.jmap.mua.MuaSession;
import rs.ltt.jmap.mua.Status;
import rs.ltt.jmap.mua.cache.*;
import rs.ltt.jmap.mua.util.QueryResult;
import rs.ltt.jmap.mua.util.QueryResultItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class QueryService extends MuaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryService.class);

    public QueryService(MuaSession muaSession) {
        super(muaSession);
    }

    public ListenableFuture<Status> refresh() {
        return Futures.transformAsync(getObjectsState(), this::refresh, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Status> refresh(ObjectsState objectsState) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        List<ListenableFuture<Status>> futuresList = refresh(objectsState, multiCall);
        multiCall.execute();
        return transform(futuresList);
    }

    private List<ListenableFuture<Status>> refresh(ObjectsState objectsState, JmapClient.MultiCall multiCall) {
        ImmutableList.Builder<ListenableFuture<Status>> futuresListBuilder = new ImmutableList.Builder<>();
        if (objectsState.mailboxState != null) {
            futuresListBuilder.add(getService(MailboxService.class).updateMailboxes(objectsState.mailboxState, multiCall));
        } else {
            futuresListBuilder.add(getService(MailboxService.class).loadMailboxes(multiCall));
        }

        //update to emails should happen before update to threads
        //when mua queries threads the corresponding emails should already be in the cache

        if (objectsState.emailState != null) {
            futuresListBuilder.add(getService(EmailService.class).updateEmails(objectsState.emailState, multiCall));
        }
        if (objectsState.threadState != null) {
            futuresListBuilder.add(getService(ThreadService.class).updateThreads(objectsState.threadState, multiCall));
        }
        return futuresListBuilder.build();
    }

    private static ListenableFuture<Status> transform(List<ListenableFuture<Status>> list) {
        return Futures.transform(Futures.allAsList(list), statuses -> {
            if (statuses.contains(Status.HAS_MORE)) {
                return Status.HAS_MORE;
            }
            if (statuses.contains(Status.UPDATED)) {
                return Status.UPDATED;
            }
            return Status.UNCHANGED;
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Status> query(Filter<Email> filter) {
        return query(EmailQuery.of(filter));
    }

    public ListenableFuture<Status> query(@NonNullDecl final EmailQuery query) {
        final ListenableFuture<QueryStateWrapper> queryStateFuture = ioExecutorService.submit(() -> cache.getQueryState(query.toQueryString()));

        return Futures.transformAsync(queryStateFuture, queryStateWrapper -> {
            Preconditions.checkNotNull(queryStateWrapper, "QueryStateWrapper can not be null");
            if (!queryStateWrapper.canCalculateChanges || queryStateWrapper.upTo == null) {
                return initialQuery(query, queryStateWrapper);
            } else {
                Preconditions.checkNotNull(queryStateWrapper.objectsState, "ObjectsState can not be null if queryState was not");
                Preconditions.checkNotNull(queryStateWrapper.objectsState.emailState, "emailState can not be null if queryState was not");
                Preconditions.checkNotNull(queryStateWrapper.objectsState.threadState, "threadState can not be null if queryState was not");
                return refreshQuery(query, queryStateWrapper);
            }
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Status> query(@NonNullDecl final EmailQuery query, final String afterEmailId) {
        final ListenableFuture<QueryStateWrapper> queryStateFuture = ioExecutorService.submit(() -> cache.getQueryState(query.toQueryString()));
        return Futures.transformAsync(
                queryStateFuture,
                queryStateWrapper -> query(query, afterEmailId, queryStateWrapper),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Status> query(@NonNullDecl final EmailQuery query, @NonNullDecl final String afterEmailId, final QueryStateWrapper queryStateWrapper) {
        Preconditions.checkNotNull(query, "Query can not be null");
        Preconditions.checkNotNull(afterEmailId, "afterEmailId can not be null");
        Preconditions.checkNotNull(queryStateWrapper, "QueryStateWrapper can not be null when paging");

        LOGGER.info("Paging query {} after {}", query.toString(), afterEmailId);

        if (queryStateWrapper.canCalculateChanges && queryStateWrapper.queryState == null) {
            throw new InconsistentQueryStateException(
                    "QueryStateWrapper needs queryState for paging when canCalculateChanges was true"
            );
        }
        if (queryStateWrapper.upTo == null || !afterEmailId.equals(queryStateWrapper.upTo.id)) {
            //in conjunction with lttrs-android this can happen if we have a QueryItemOverwrite for the last item in
            //a query. This will probably fix itself once the update command has run as well as a subsequent updateQuery() call.
            throw new InconsistentQueryStateException("upToId from QueryState needs to match the supplied afterEmailId");
        }
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> queryRefreshFuture;
        if (queryStateWrapper.canCalculateChanges) {
            queryRefreshFuture = refreshQuery(query, queryStateWrapper, multiCall);
        } else {
            LOGGER.debug("Skipping queryChanges because canCalculateChanges was false");
            queryRefreshFuture = null;
        }

        final JmapRequest.Call queryCall = multiCall.call(
                QueryEmailMethodCall.builder()
                        .accountId(accountId)
                        .query(query)
                        .anchor(afterEmailId)
                        .limit(getQueryPageSize())
                        .build()
        );
        final ListenableFuture<MethodResponses> queryResponsesFuture = queryCall.getMethodResponses();
        final ListenableFuture<MethodResponses> getThreadIdsResponsesFuture = multiCall.call(
                GetEmailMethodCall.builder()
                        .accountId(accountId)
                        .idsReference(queryCall.createResultReference(Request.Invocation.ResultReference.Path.IDS))
                        .properties(Email.Properties.THREAD_ID)
                        .build()
        ).getMethodResponses();

        final ListenableFuture<QueryResult> queryResultFuture = QueryResult.of(queryResponsesFuture, getThreadIdsResponsesFuture);

        queryResultFuture.addListener(() -> {
            try {
                final QueryResult queryResult = queryResultFuture.get();

                //processing order is:
                //  1) refresh the existent query (which in our implementation also piggybacks email and thread updates)
                //  2) store new items

                if (queryRefreshFuture != null) {
                    queryRefreshFuture.get();
                }

                addQueryResult(query, afterEmailId, queryResult);

                fetchMissing(query.toQueryString())
                        .addListener(
                                () -> settableFuture.set(queryResult.items.length > 0 ? Status.UPDATED : Status.UNCHANGED),
                                MoreExecutors.directExecutor()
                        );
            } catch (ExecutionException exception) {
                invalidateQueryCache(queryRefreshFuture, query, exception);
                settableFuture.setException(extractException(exception));
            } catch (Exception e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);
        multiCall.execute();
        return settableFuture;
    }

    private void addQueryResult(final EmailQuery query, String afterEmailId, final QueryResult queryResult) throws CacheWriteException {
        try {
            cache.addQueryResult(query.toQueryString(), afterEmailId, queryResult);
        } catch (final CorruptCacheException e) {
            LOGGER.info("Invalidating query result cache after cache corruption", e);
            cache.invalidateQueryResult(query.toQueryString());
            throw e;
        }
    }

    private void invalidateQueryCache(final ListenableFuture<Status> queryRefreshFuture,
                                      final EmailQuery query,
                                      final ExecutionException exception) {
        final Throwable cause = exception.getCause();
        //check if cache needs invalidation pull into separate method
        if (cause instanceof MethodErrorResponseException) {
            final MethodErrorResponse methodError = ((MethodErrorResponseException) cause).getMethodErrorResponse();
            if (methodError instanceof AnchorNotFoundMethodErrorResponse) {
                if (queryRefreshFuture == null || Status.unchanged(queryRefreshFuture)) {
                    LOGGER.info("Invalidating query result cache after receiving AnchorNotFound response");
                    cache.invalidateQueryResult(query.toQueryString());
                } else {
                    LOGGER.info(
                            "Holding back on invaliding query result cache despite AnchorNotFound response because query refresh had changes"
                    );
                }
            }
        }

    }

    private ListenableFuture<Status> refreshQuery(@NonNullDecl final EmailQuery query, @NonNullDecl final QueryStateWrapper queryStateWrapper) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        ListenableFuture<Status> future = refreshQuery(query, queryStateWrapper, multiCall);
        multiCall.execute();
        return future;
    }

    private ListenableFuture<Status> refreshQuery(@NonNullDecl final EmailQuery query,
                                                  @NonNullDecl final QueryStateWrapper queryStateWrapper,
                                                  final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(queryStateWrapper.queryState, "QueryState can not be null when attempting to refresh query");
        LOGGER.info("Refreshing query {}", query.toString());
        final SettableFuture<Status> settableFuture = SettableFuture.create();

        final List<ListenableFuture<Status>> piggyBackedFuturesList = refresh(queryStateWrapper.objectsState, multiCall);

        final JmapRequest.Call queryChangesCall = multiCall.call(
                //TODO do we want to include upTo?
                QueryChangesEmailMethodCall.builder()
                        .accountId(accountId)
                        .sinceQueryState(queryStateWrapper.queryState)
                        .query(query)
                        .build()
        );
        final ListenableFuture<MethodResponses> queryChangesResponsesFuture = queryChangesCall.getMethodResponses();
        final ListenableFuture<MethodResponses> getThreadIdResponsesFuture = multiCall.call(
                GetEmailMethodCall.builder()
                        .accountId(accountId)
                        .idsReference(queryChangesCall.createResultReference(Request.Invocation.ResultReference.Path.ADDED_IDS))
                        .properties(Email.Properties.THREAD_ID)
                        .build()
        ).getMethodResponses();

        queryChangesResponsesFuture.addListener(() -> {
            try {
                QueryChangesEmailMethodResponse queryChangesResponse = queryChangesResponsesFuture.get().getMain(QueryChangesEmailMethodResponse.class);
                GetEmailMethodResponse getThreadIdsResponse = getThreadIdResponsesFuture.get().getMain(GetEmailMethodResponse.class);
                List<AddedItem<QueryResultItem>> added = QueryResult.of(queryChangesResponse, getThreadIdsResponse);

                final QueryUpdate<Email, QueryResultItem> queryUpdate = QueryUpdate.of(queryChangesResponse, added);

                //processing order is:
                //  1) update Objects (Email, Threads, and Mailboxes)
                //  2) store query results; If query cache sees an outdated email state it will fail

                Status piggybackStatus = transform(piggyBackedFuturesList).get(); //wait for updates before attempting to fetch
                Status queryUpdateStatus = Status.of(queryUpdate);

                if (queryUpdate.hasChanges()) {
                    cache.updateQueryResults(query.toQueryString(), queryUpdate, getThreadIdsResponse.getTypedState());
                }


                final List<ListenableFuture<Status>> list = new ArrayList<>();
                list.add(Futures.immediateFuture(piggybackStatus));
                list.add(Futures.immediateFuture(queryUpdateStatus));
                //it might be that a previous fetchMissing() has failed. so better safe than sorry
                list.add(fetchMissing(query.toQueryString()));
                settableFuture.setFuture(transform(list));

            } catch (InterruptedException | ExecutionException | CacheWriteException | CacheConflictException e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);

        return settableFuture;
    }

    private ListenableFuture<Status> initialQuery(@NonNullDecl final EmailQuery query, @NonNullDecl final QueryStateWrapper queryStateWrapper) {
        return Futures.transformAsync(
                jmapClient.getSession(), session -> initialQuery(query, queryStateWrapper, Preconditions.checkNotNull(session, "Session object must not be null")),
                MoreExecutors.directExecutor()
        );

    }

    private ListenableFuture<Status> initialQuery(@NonNullDecl final EmailQuery query, @NonNullDecl final QueryStateWrapper queryStateWrapper, @NonNullDecl Session session) {

        Preconditions.checkState(
                !queryStateWrapper.canCalculateChanges || queryStateWrapper.upTo == null,
                "canCalculateChanges must be false or upTo must be NULL when calling initialQuery"
        );

        LOGGER.info("Performing initial query for {}", query.toString());
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        JmapClient.MultiCall multiCall = jmapClient.newMultiCall();

        //these need to be processed *before* the Query call or else the fetchMissing will not honor newly fetched ids
        final List<ListenableFuture<Status>> piggyBackedFuturesList = refresh(queryStateWrapper.objectsState, multiCall);

        final JmapRequest.Call queryCall = multiCall.call(
                QueryEmailMethodCall.builder()
                        .accountId(accountId)
                        .query(query)
                        .limit(calculateQueryPageSize(queryStateWrapper, session))
                        .build()
        );
        final ListenableFuture<MethodResponses> queryResponsesFuture = queryCall.getMethodResponses();
        final JmapRequest.Call threadIdsCall = multiCall.call(
                GetEmailMethodCall.builder()
                        .accountId(accountId)
                        .idsReference(queryCall.createResultReference(Request.Invocation.ResultReference.Path.IDS))
                        .properties(Email.Properties.THREAD_ID)
                        .build()
        );
        final ListenableFuture<MethodResponses> getThreadIdsResponsesFuture = threadIdsCall.getMethodResponses();


        final ListenableFuture<MethodResponses> getThreadsResponsesFuture;
        final ListenableFuture<MethodResponses> getEmailResponsesFuture;
        if (queryStateWrapper.objectsState.threadState == null && queryStateWrapper.objectsState.emailState == null) {
            final JmapRequest.Call threadCall = multiCall.call(
                    GetThreadMethodCall.builder()
                            .accountId(accountId)
                            .idsReference(threadIdsCall.createResultReference(Request.Invocation.ResultReference.Path.LIST_THREAD_IDS))
                            .build()
            );
            getThreadsResponsesFuture = threadCall.getMethodResponses();
            getEmailResponsesFuture = multiCall.call(
                    GetEmailMethodCall.builder()
                            .accountId(accountId)
                            .idsReference(threadCall.createResultReference(Request.Invocation.ResultReference.Path.LIST_EMAIL_IDS))
                            .fetchTextBodyValues(true)
                            .build()
            ).getMethodResponses();
        } else {
            getThreadsResponsesFuture = null;
            getEmailResponsesFuture = null;
        }

        multiCall.execute();
        queryResponsesFuture.addListener(() -> {
            try {
                QueryEmailMethodResponse queryResponse = queryResponsesFuture.get().getMain(QueryEmailMethodResponse.class);
                GetEmailMethodResponse getThreadIdsResponse = getThreadIdsResponsesFuture.get().getMain(GetEmailMethodResponse.class);

                final QueryResult queryResult = QueryResult.of(queryResponse, getThreadIdsResponse);

                //processing order is:
                //  1) update Objects (Email, Threads, and Mailboxes)
                //  2) if getThread or getEmails calls where made process those results
                //  3) store query results; If query cache sees an outdated email state it will fail
                transform(piggyBackedFuturesList).get();

                if (getThreadsResponsesFuture != null && getEmailResponsesFuture != null) {
                    GetThreadMethodResponse getThreadsResponse = getThreadsResponsesFuture.get().getMain(GetThreadMethodResponse.class);
                    GetEmailMethodResponse getEmailResponse = getEmailResponsesFuture.get().getMain(GetEmailMethodResponse.class);
                    cache.setThreadsAndEmails(getThreadsResponse.getTypedState(), getThreadsResponse.getList(), getEmailResponse.getTypedState(), getEmailResponse.getList());
                }

                if (queryResult.position != 0) {
                    throw new IllegalStateException("Server reported position " + queryResult.position + " in response to initial query. We expected 0");
                }

                cache.setQueryResult(query.toQueryString(), queryResult);

                if (getThreadsResponsesFuture != null && getEmailResponsesFuture != null) {
                    settableFuture.set(Status.UPDATED);
                } else {
                    List<ListenableFuture<Status>> list = new ArrayList<>();
                    list.add(Futures.immediateFuture(Status.UPDATED));
                    list.add(fetchMissing(query.toQueryString()));
                    settableFuture.setFuture(transform(list));
                }
            } catch (InterruptedException | ExecutionException | CacheWriteException e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);
        return settableFuture;
    }

    //TODO we need to test this
    private Long calculateQueryPageSize(final QueryStateWrapper queryStateWrapper, final Session session) {
        final Long configuredQueryPageSize = getQueryPageSize();
        if (queryStateWrapper.upTo != null) {
            final long currentNumberOfItemsInCache = queryStateWrapper.upTo.position + 1;
            if (configuredQueryPageSize == null || currentNumberOfItemsInCache > configuredQueryPageSize) {
                LOGGER.info("Current number of items ({}) in query cache exceeds configured page size of {}", currentNumberOfItemsInCache, configuredQueryPageSize);
                final long maxObjectsInGet = session.getCapability(CoreCapability.class).maxObjectsInGet();
                if (maxObjectsInGet < currentNumberOfItemsInCache) {
                    LOGGER.warn("Capping page size at {} to not exceed maxObjectsInGet", maxObjectsInGet);
                    return maxObjectsInGet;
                } else {
                    return currentNumberOfItemsInCache;
                }
            } else {
                return configuredQueryPageSize;
            }
        } else {
            return configuredQueryPageSize;
        }
    }

    private ListenableFuture<Status> fetchMissing(@NonNullDecl final String queryString) {
        Preconditions.checkNotNull(queryString, "QueryString can not be null");
        try {
            return fetchMissing(cache.getMissing(queryString));
        } catch (CacheReadException e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<Status> fetchMissing(final Missing missing) {
        Preconditions.checkNotNull(missing, "Missing can not be null");
        Preconditions.checkNotNull(missing.threadIds, "Missing.ThreadIds can not be null; pass empty list instead");
        if (missing.threadIds.size() == 0) {
            return Futures.immediateFuture(Status.UNCHANGED);
        }
        LOGGER.info("fetching " + missing.threadIds.size() + " missing threads");
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> updateThreadsFuture = getService(ThreadService.class).updateThreads(missing.threadState, multiCall);
        final ListenableFuture<Status> updateEmailsFuture = getService(EmailService.class).updateEmails(missing.emailState, multiCall);
        final JmapRequest.Call threadsCall = multiCall.call(
                GetThreadMethodCall.builder()
                        .accountId(accountId)
                        .ids(missing.threadIds.toArray(new String[0]))
                        .build()
        );
        final ListenableFuture<MethodResponses> getThreadsResponsesFuture = threadsCall.getMethodResponses();
        final ListenableFuture<MethodResponses> getEmailsResponsesFuture = multiCall.call(
                GetEmailMethodCall.builder()
                        .accountId(accountId)
                        .idsReference(threadsCall.createResultReference(Request.Invocation.ResultReference.Path.LIST_EMAIL_IDS))
                        .fetchTextBodyValues(true)
                        .build()
        ).getMethodResponses();
        multiCall.execute();
        getThreadsResponsesFuture.addListener(() -> {
            try {
                Status updateThreadsStatus = updateThreadsFuture.get();
                if (updateThreadsStatus == Status.HAS_MORE) {
                    //throw
                }

                Status updateEmailStatus = updateEmailsFuture.get();
                if (updateEmailStatus == Status.HAS_MORE) {
                    //throw
                }

                GetThreadMethodResponse getThreadMethodResponse = getThreadsResponsesFuture.get().getMain(GetThreadMethodResponse.class);
                GetEmailMethodResponse getEmailMethodResponse = getEmailsResponsesFuture.get().getMain(GetEmailMethodResponse.class);
                cache.addThreadsAndEmail(getThreadMethodResponse.getTypedState(), getThreadMethodResponse.getList(), getEmailMethodResponse.getTypedState(), getEmailMethodResponse.getList());

                settableFuture.set(Status.UPDATED);

            } catch (Exception e) {
                settableFuture.setException(extractException(e));
            }

        }, ioExecutorService);
        return settableFuture;
    }
}
