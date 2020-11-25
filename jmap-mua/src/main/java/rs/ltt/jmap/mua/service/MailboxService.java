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
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.method.call.mailbox.GetMailboxMethodCall;
import rs.ltt.jmap.common.method.call.mailbox.SetMailboxMethodCall;
import rs.ltt.jmap.common.method.response.mailbox.ChangesMailboxMethodResponse;
import rs.ltt.jmap.common.method.response.mailbox.GetMailboxMethodResponse;
import rs.ltt.jmap.common.method.response.mailbox.SetMailboxMethodResponse;
import rs.ltt.jmap.mua.MuaSession;
import rs.ltt.jmap.mua.SetMailboxException;
import rs.ltt.jmap.mua.Status;
import rs.ltt.jmap.mua.cache.CacheWriteException;
import rs.ltt.jmap.mua.cache.ObjectsState;
import rs.ltt.jmap.mua.cache.Update;
import rs.ltt.jmap.mua.util.CreateUtil;
import rs.ltt.jmap.mua.util.MailboxUtil;
import rs.ltt.jmap.mua.util.UpdateUtil;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

public class MailboxService extends MuaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxService.class);

    public MailboxService(MuaSession muaSession) {
        super(muaSession);
    }

    public ListenableFuture<Status> refreshMailboxes() {
        final ListenableFuture<String> mailboxStateFuture = ioExecutorService.submit(cache::getMailboxState);

        return Futures.transformAsync(mailboxStateFuture, state -> {
            if (state == null) {
                return loadMailboxes();
            } else {
                return updateMailboxes(state);
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Status> loadMailboxes() {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> future = loadMailboxes(multiCall);
        multiCall.execute();
        return future;
    }

    protected ListenableFuture<Status> loadMailboxes(final JmapClient.MultiCall multiCall) {
        LOGGER.info("Fetching mailboxes");
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final ListenableFuture<MethodResponses> getMailboxMethodResponsesFuture = multiCall.call(
                GetMailboxMethodCall.builder().accountId(accountId).build()
        ).getMethodResponses();
        getMailboxMethodResponsesFuture.addListener(() -> {
            try {
                GetMailboxMethodResponse response = getMailboxMethodResponsesFuture.get().getMain(GetMailboxMethodResponse.class);
                Mailbox[] mailboxes = response.getList();
                cache.setMailboxes(response.getTypedState(), mailboxes);
                settableFuture.set(Status.of(mailboxes.length > 0));
            } catch (InterruptedException | ExecutionException | CacheWriteException e) {
                settableFuture.setException(extractException(e));
            }

        }, ioExecutorService);
        return settableFuture;
    }

    private ListenableFuture<Status> updateMailboxes(final String state) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> future = updateMailboxes(state, multiCall);
        multiCall.execute();
        return future;
    }

    protected ListenableFuture<Status> updateMailboxes(final String state, final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(state, "State can not be null when updating mailboxes");
        LOGGER.info("Refreshing mailboxes since state {}", state);
        final UpdateUtil.MethodResponsesFuture methodResponsesFuture = UpdateUtil.mailboxes(multiCall, accountId, state);
        registerCacheInvalidationCallback(methodResponsesFuture, this::invalidateCache);
        return methodResponsesFuture.addCallback(() -> {
            final ChangesMailboxMethodResponse changesResponse = methodResponsesFuture.changes(ChangesMailboxMethodResponse.class);
            final GetMailboxMethodResponse createdResponse = methodResponsesFuture.created(GetMailboxMethodResponse.class);
            final GetMailboxMethodResponse updatedResponse = methodResponsesFuture.updated(GetMailboxMethodResponse.class);
            final Update<Mailbox> update = Update.of(changesResponse, createdResponse, updatedResponse);
            if (update.hasChanges()) {
                cache.updateMailboxes(update, changesResponse.getUpdatedProperties());
            }
            return Futures.immediateFuture(Status.of(update));
        }, ioExecutorService);
    }

    private void invalidateCache() {
        LOGGER.info("Invalidate mailboxes cache after cannotCalculateChanges response");
        this.cache.invalidateMailboxes();
    }

    protected ListenableFuture<Collection<? extends IdentifiableMailboxWithRole>> getMailboxes() {
        return ioExecutorService.submit(cache::getSpecialMailboxes);
    }

    public ListenableFuture<Boolean> createMailbox(final Mailbox mailbox) {
        ListenableFuture<MethodResponses> future = jmapClient.call(
                SetMailboxMethodCall.builder()
                        .accountId(accountId)
                        .create(ImmutableMap.of("new-mailbox-0", mailbox))
                        .build()
        );
        return Futures.transformAsync(future, methodResponses -> {
            SetMailboxMethodResponse response = methodResponses.getMain(SetMailboxMethodResponse.class);
            SetMailboxException.throwIfFailed(response);
            return Futures.immediateFuture(response.getUpdatedCreatedCount() > 0);
        }, MoreExecutors.directExecutor());
    }

    /**
     * Utility method to create a mailbox of a certain role. This method is usually used in conjunction with a
     * modification of emails in a thread. For example a generalized 'move to archive' method can call this method
     * ahead of time to create the archive mailbox.
     * <p>
     * When called with an ObjectsState the SetMailboxMethodCall will be guarded with an ifInState parameter. In that case
     * an automatic call to updateMailboxes is made as well - The idea behind that approach is that if the ifInState fails
     * we will at least have an up to date state on the next attempt.
     *
     * @param role         The role of the mailbox we want to create.
     * @param objectsState If an ObjectsState is giving the create call will be guarded with a ifInState
     * @param multiCall    The MultiCall that will later have the SetEmailMethodCall added to it.
     * @return
     */
    protected ListenableFuture<MethodResponses> createMailbox(@NonNullDecl final Role role, @NullableDecl ObjectsState objectsState, final JmapClient.MultiCall multiCall) {
        final SetMailboxMethodCall setMailboxMethodCall = SetMailboxMethodCall.builder()
                .accountId(this.accountId)
                .ifInState(objectsState == null ? null : objectsState.mailboxState)
                .create(ImmutableMap.of(CreateUtil.createId(role), MailboxUtil.create(role)))
                .build();
        final ListenableFuture<MethodResponses> future = multiCall.call(setMailboxMethodCall).getMethodResponses();
        if (objectsState != null && objectsState.mailboxState != null) {
            updateMailboxes(objectsState.mailboxState, multiCall);
        }
        return future;
    }

}
