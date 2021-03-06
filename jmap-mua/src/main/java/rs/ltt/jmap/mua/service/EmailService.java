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
import rs.ltt.jmap.client.JmapRequest;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.common.Request;
import rs.ltt.jmap.common.entity.*;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.method.call.email.QueryEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.SetEmailMethodCall;
import rs.ltt.jmap.common.method.call.submission.SetEmailSubmissionMethodCall;
import rs.ltt.jmap.common.method.response.email.ChangesEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.GetEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.SetEmailMethodResponse;
import rs.ltt.jmap.common.method.response.mailbox.SetMailboxMethodResponse;
import rs.ltt.jmap.common.method.response.submission.SetEmailSubmissionMethodResponse;
import rs.ltt.jmap.common.util.Patches;
import rs.ltt.jmap.mua.*;
import rs.ltt.jmap.mua.cache.CacheConflictException;
import rs.ltt.jmap.mua.cache.CacheWriteException;
import rs.ltt.jmap.mua.cache.ObjectsState;
import rs.ltt.jmap.mua.cache.Update;
import rs.ltt.jmap.mua.util.CreateUtil;
import rs.ltt.jmap.mua.util.MailboxUtil;
import rs.ltt.jmap.mua.util.UpdateUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class EmailService extends MuaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);


    public EmailService(MuaSession muaSession) {
        super(muaSession);
    }

    public ListenableFuture<String> draft(final Email email) {
        return Futures.transformAsync(
                getService(MailboxService.class).getMailboxes(),
                mailboxes -> draft(email, MailboxUtil.find(mailboxes, Role.DRAFTS)),
                MoreExecutors.directExecutor()
        );
    }

    public ListenableFuture<String> draft(final Email email, final IdentifiableMailboxWithRole drafts) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<String> future = draft(email, drafts, multiCall);
        multiCall.execute();
        return future;
    }

    private ListenableFuture<String> draft(final Email email, final IdentifiableMailboxWithRole drafts, final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(email, "Email can not be null when attempting to create a draft");
        Preconditions.checkState(email.getId() == null, "id is a server-set property");
        Preconditions.checkState(email.getBlobId() == null, "blobId is a server-set property");
        Preconditions.checkState(email.getThreadId() == null, "threadId is a server-set property");
        final Email.EmailBuilder emailBuilder = email.toBuilder();
        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (drafts == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.DRAFTS, null, multiCall);
        } else {
            mailboxCreateFuture = null;
        }
        if (drafts == null) {
            emailBuilder.mailboxId(CreateUtil.createIdReference(Role.DRAFTS), true);
        } else {
            emailBuilder.mailboxId(drafts.getId(), true);
        }
        emailBuilder.keyword(Keyword.DRAFT, true);
        emailBuilder.keyword(Keyword.SEEN, true);
        final ListenableFuture<MethodResponses> future = multiCall.call(
                SetEmailMethodCall.builder()
                        .accountId(accountId)
                        .create(ImmutableMap.of(CreateUtil.EMAIL_CREATION_ID, emailBuilder.build()))
                        .build()
        ).getMethodResponses();
        return Futures.transformAsync(future, methodResponses -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            final SetEmailMethodResponse setEmailMethodResponse = methodResponses.getMain(SetEmailMethodResponse.class);
            SetEmailException.throwIfFailed(setEmailMethodResponse);
            final Map<String, Email> created = setEmailMethodResponse.getCreated();
            final Email email1 = created != null ? created.get(CreateUtil.EMAIL_CREATION_ID) : null;
            if (email1 != null) {
                return Futures.immediateFuture(email1.getId());
            } else {
                throw new IllegalStateException("Unable to find email id in method response");
            }
        }, MoreExecutors.directExecutor());

    }

    public ListenableFuture<Boolean> submit(final Email email, final Identity identity) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole drafts = MailboxUtil.find(mailboxes, Role.DRAFTS);
            final String draftMailboxId;
            if (drafts == null || !email.getMailboxIds().containsKey(drafts.getId())) {
                draftMailboxId = null;
            } else {
                draftMailboxId = drafts.getId();
            }
            final IdentifiableMailboxWithRole sent = MailboxUtil.find(mailboxes, Role.SENT);
            return submit(email.getId(), identity, draftMailboxId, sent);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> submit(final String emailId, final IdentifiableIdentity identity, @NullableDecl String draftMailboxId, final IdentifiableMailboxWithRole sent) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Boolean> future = submit(emailId, identity, draftMailboxId, sent, multiCall);
        multiCall.execute();
        return future;
    }

    //TODO the draftMailboxId is unused
    private ListenableFuture<Boolean> submit(@NonNullDecl final String emailId, @NonNullDecl final IdentifiableIdentity identity, @NullableDecl String draftMailboxId, @NullableDecl final IdentifiableMailboxWithRole sent, final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(emailId, "emailId can not be null when attempting to submit");
        Preconditions.checkNotNull(identity, "identity can not be null when attempting to submit an email");
        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (sent == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.SENT, null, multiCall);
        } else {
            mailboxCreateFuture = null;
        }
        final Patches.Builder patchesBuilder = Patches.builder();
        patchesBuilder.remove("keywords/" + Keyword.DRAFT);
        patchesBuilder.set("mailboxIds", ImmutableMap.of(sent == null ? CreateUtil.createIdReference(Role.SENT) : sent.getId(), true));
        final ListenableFuture<MethodResponses> setEmailSubmissionFuture = multiCall.call(
                SetEmailSubmissionMethodCall.builder()
                        .accountId(accountId)
                        .create(
                                ImmutableMap.of(
                                        "es0",
                                        EmailSubmission.builder()
                                                .emailId(emailId)
                                                .identityId(identity.getId())
                                                .build()
                                )
                        )
                        .onSuccessUpdateEmail(
                                ImmutableMap.of(
                                        "#es0",
                                        patchesBuilder.build()
                                )
                        )
                        .build()
        ).getMethodResponses();
        return Futures.transformAsync(setEmailSubmissionFuture, methodResponses -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            SetEmailSubmissionMethodResponse setEmailSubmissionMethodResponse = methodResponses.getMain(SetEmailSubmissionMethodResponse.class);
            SetEmailSubmissionException.throwIfFailed(setEmailSubmissionMethodResponse);
            return Futures.immediateFuture(setEmailSubmissionMethodResponse.getUpdatedCreatedCount() > 0);
        }, MoreExecutors.directExecutor());

    }

    public ListenableFuture<Boolean> submit(final String emailId, final IdentifiableIdentity identity) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole drafts = MailboxUtil.find(mailboxes, Role.DRAFTS);
            final IdentifiableMailboxWithRole sent = MailboxUtil.find(mailboxes, Role.SENT);
            return submit(emailId, identity, drafts == null ? null : drafts.getId(), sent);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<String> send(final Email email, final IdentifiableIdentity identity) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole draft = MailboxUtil.find(mailboxes, Role.DRAFTS);
            final IdentifiableMailboxWithRole sent = MailboxUtil.find(mailboxes, Role.SENT);
            return send(email, identity, draft, sent);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<String> send(final Email email,
                                          final IdentifiableIdentity identity,
                                          final IdentifiableMailboxWithRole drafts,
                                          final IdentifiableMailboxWithRole sent) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<String> draftFuture = draft(email, drafts, multiCall);
        final ListenableFuture<Boolean> submitFuture = submit(CreateUtil.EMAIL_CREATION_ID_REFERENCE, identity, drafts == null ? CreateUtil.createIdReference(Role.DRAFTS) : drafts.getId(), sent, multiCall);
        multiCall.execute();
        return Futures.transformAsync(submitFuture, success -> draftFuture, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> setKeyword(final Collection<? extends IdentifiableEmailWithKeywords> emails, final String keyword) {
        return Futures.transformAsync(getObjectsState(), objectsState -> setKeyword(emails, keyword, objectsState), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> setKeyword(final Collection<? extends IdentifiableEmailWithKeywords> emails,
                                                 final String keyword,
                                                 final ObjectsState objectsState) {
        final ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithKeywords email : emails) {
            if (!email.getKeywords().containsKey(keyword)) {
                emailPatchObjectMapBuilder.put(email.getId(), Patches.set("keywords/" + keyword, true));
            }
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        return applyEmailPatches(patches, objectsState);
    }

    private ListenableFuture<Boolean> applyEmailPatches(final Map<String, Map<String, Object>> patches, final ObjectsState objectsState) {
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }
        JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        ListenableFuture<Boolean> future = applyEmailPatches(patches, objectsState, true, multiCall);
        multiCall.execute();
        return future;
    }

    /**
     * Utility method to update emails with a given patch set. Optionally this call will be guarded with an ifInState
     * parameter. If it is this method will also chain an updateEmailCall right after it. The rational for that is that
     * when the first call fails with a miss matched state a second attempt (not triggered automatically) will most likely
     * succeed.
     * <p>
     * When calling this method after creating a mailbox ifInState should be set to false; Otherwise the createMailbox
     * call will increase the state and the subsequent setEmail call would fail.
     *
     * @param patches      The map of patches
     * @param objectsState An ObjectsState that will be used to guard the set call. Only used when ifInState is true
     * @param ifInState    Whether or not to guard the call
     * @param multiCall
     * @return
     */
    private ListenableFuture<Boolean> applyEmailPatches(final Map<String, Map<String, Object>> patches,
                                                        final ObjectsState objectsState,
                                                        final boolean ifInState,
                                                        final JmapClient.MultiCall multiCall) {
        if (ifInState) {
            Preconditions.checkNotNull(objectsState);
        }
        final ListenableFuture<MethodResponses> future = multiCall.call(
                SetEmailMethodCall.builder()
                        .accountId(accountId)
                        .ifInState(ifInState ? objectsState.emailState : null)
                        .update(patches)
                        .build()
        ).getMethodResponses();
        if (ifInState && objectsState.emailState != null) {
            updateEmails(objectsState.emailState, multiCall);
        }
        return Futures.transformAsync(future, methodResponses -> {
            SetEmailMethodResponse setEmailMethodResponse = methodResponses.getMain(SetEmailMethodResponse.class);
            SetEmailException.throwIfFailed(setEmailMethodResponse);
            return Futures.immediateFuture(setEmailMethodResponse.getUpdatedCreatedCount() > 0);
        }, ioExecutorService);
    }

    protected ListenableFuture<Status> updateEmails(final String state, final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(state, "state can not be null when updating emails");
        LOGGER.info("Refreshing emails since state {}", state);
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final UpdateUtil.MethodResponsesFuture methodResponsesFuture = UpdateUtil.emails(multiCall, accountId, state);
        methodResponsesFuture.addListener(() -> {
            try {
                final ChangesEmailMethodResponse changesResponse = methodResponsesFuture.changes(ChangesEmailMethodResponse.class);
                final GetEmailMethodResponse createdResponse = methodResponsesFuture.created(GetEmailMethodResponse.class);
                final GetEmailMethodResponse updatedResponse = methodResponsesFuture.updated(GetEmailMethodResponse.class);
                final Update<Email> update = Update.of(changesResponse, createdResponse, updatedResponse);
                if (update.hasChanges()) {
                    cache.updateEmails(update, Email.Properties.MUTABLE);
                }
                settableFuture.set(Status.of(update));
            } catch (InterruptedException | ExecutionException | CacheWriteException | CacheConflictException e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);
        return settableFuture;
    }

    public ListenableFuture<Boolean> discardDraft(final @NonNullDecl IdentifiableEmailWithKeywords email) {
        Preconditions.checkNotNull(email);
        Preconditions.checkArgument(email.getKeywords().containsKey(Keyword.DRAFT), "Email does not have $draft keyword");
        return Futures.transformAsync(getObjectsState(), objectsState -> discardDraft(email, objectsState), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> discardDraft(final @NonNullDecl IdentifiableEmailWithKeywords email, final ObjectsState objectsState) {
        Preconditions.checkNotNull(objectsState);
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Boolean> future = discardDraft(email, objectsState, multiCall);
        multiCall.execute();
        return future;
    }

    private ListenableFuture<Boolean> discardDraft(final @NonNullDecl IdentifiableEmailWithKeywords email,
                                                   final ObjectsState objectsState,
                                                   final JmapClient.MultiCall multiCall) {
        final ListenableFuture<MethodResponses> future = multiCall.call(
                SetEmailMethodCall.builder()
                        .accountId(accountId)
                        .ifInState(objectsState.emailState)
                        .destroy(new String[]{email.getId()})
                        .build()
        ).getMethodResponses();
        if (objectsState.emailState != null) {
            updateEmails(objectsState.emailState, multiCall);
        }
        return Futures.transformAsync(future, methodResponses -> {
            SetEmailMethodResponse setEmailMethodResponse = methodResponses.getMain(SetEmailMethodResponse.class);
            SetEmailException.throwIfFailed(setEmailMethodResponse);
            final String[] destroyed = setEmailMethodResponse.getDestroyed();
            return Futures.immediateFuture(destroyed != null && destroyed.length > 0);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> removeKeyword(final Collection<? extends IdentifiableEmailWithKeywords> emails,
                                                   final String keyword) {
        return Futures.transformAsync(
                getObjectsState(),
                objectsState -> removeKeyword(emails, keyword, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> removeKeyword(final Collection<? extends IdentifiableEmailWithKeywords> emails,
                                                    final String keyword,
                                                    final ObjectsState objectsState) {
        final ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithKeywords email : emails) {
            if (email.getKeywords().containsKey(keyword)) {
                emailPatchObjectMapBuilder.put(email.getId(), Patches.remove("keywords/" + keyword));
            }
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        return applyEmailPatches(patches, objectsState);
    }

    public ListenableFuture<Boolean> copyToImportant(@NonNullDecl final Collection<? extends IdentifiableEmailWithMailboxIds> emails) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole important = MailboxUtil.find(mailboxes, Role.IMPORTANT);
            return copyToImportant(emails, important);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> copyToImportant(@NonNullDecl final Collection<? extends IdentifiableEmailWithMailboxIds> emails, @NullableDecl final IdentifiableMailboxWithRole important) {
        return Futures.transformAsync(getObjectsState(), objectsState -> copyToImportant(emails, important, objectsState), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> copyToImportant(@NonNullDecl final Collection<? extends IdentifiableEmailWithMailboxIds> emails, @NullableDecl final IdentifiableMailboxWithRole important, final ObjectsState objectsState) {
        Preconditions.checkNotNull(emails, "emails can not be null when attempting to copy them to important");
        if (important != null) {
            Preconditions.checkArgument(important.getRole() == Role.IMPORTANT, "Supplied important mailbox must have the role IMPORTANT");
        }
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();

        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (important == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.IMPORTANT, objectsState, multiCall);
        } else {
            mailboxCreateFuture = null;
        }

        ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithMailboxIds email : emails) {
            final Map<String, Boolean> mailboxIds = new HashMap<>(email.getMailboxIds());
            if (important == null) {
                mailboxIds.put(CreateUtil.createIdReference(Role.IMPORTANT), true);
            } else {
                mailboxIds.put(important.getId(), true);
            }
            if (!mailboxIds.equals(email.getMailboxIds())) {
                emailPatchObjectMapBuilder.put(email.getId(), Patches.set("mailboxIds", mailboxIds));
            }
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }

        final ListenableFuture<Boolean> patchesFuture = applyEmailPatches(patches,
                objectsState,
                important != null, //if do not have to create a mailbox in the same call, calling ifInState on the emails is fine
                multiCall
        );

        multiCall.execute();

        return Futures.transformAsync(patchesFuture, patchesResults -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            return Futures.immediateFuture(patchesResults);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> copyToMailbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                   final IdentifiableMailboxWithRole mailbox) {
        return Futures.transformAsync(
                getObjectsState(), objectsState -> copyToMailbox(emails, mailbox, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> copyToMailbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails, final IdentifiableMailboxWithRole mailbox, final ObjectsState objectsState) {
        ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithMailboxIds email : emails) {
            if (email.getMailboxIds().containsKey(mailbox.getId())) {
                continue;
            }
            Patches.Builder patchesBuilder = Patches.builder();
            patchesBuilder.set("mailboxIds/" + mailbox.getId(), true);
            emailPatchObjectMapBuilder.put(email.getId(), patchesBuilder.build());
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();

        return applyEmailPatches(patches, objectsState);
    }

    public ListenableFuture<Boolean> moveToInbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole archive = MailboxUtil.find(mailboxes, Role.ARCHIVE);
            final IdentifiableMailboxWithRole trash = MailboxUtil.find(mailboxes, Role.TRASH);
            final IdentifiableMailboxWithRole inbox = MailboxUtil.find(mailboxes, Role.INBOX);
            return moveToInbox(emails, archive, trash, inbox);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> moveToInbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                  final IdentifiableMailboxWithRole archive,
                                                  final IdentifiableMailboxWithRole trash,
                                                  final IdentifiableMailboxWithRole inbox) {
        return Futures.transformAsync(
                getObjectsState(), objectsState -> moveToInbox(emails, archive, trash, inbox, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> moveToInbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                  final IdentifiableMailboxWithRole archive,
                                                  final IdentifiableMailboxWithRole trash,
                                                  final IdentifiableMailboxWithRole inbox,
                                                  final ObjectsState objectsState) {
        Preconditions.checkNotNull(emails, "emails can not be null when attempting to move them to inbox");

        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();

        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (inbox == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.INBOX, objectsState, multiCall);
        } else {
            mailboxCreateFuture = null;
        }

        final ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (final IdentifiableEmailWithMailboxIds email : emails) {
            final Map<String, Boolean> mailboxIds = new HashMap<>(email.getMailboxIds());
            if (archive != null) {
                mailboxIds.remove(archive.getId());
            }
            if (trash != null) {
                mailboxIds.remove(trash.getId());
            }
            if (inbox == null) {
                mailboxIds.put(CreateUtil.createIdReference(Role.INBOX), true);
            } else {
                mailboxIds.put(inbox.getId(), true);
            }
            if (!mailboxIds.equals(email.getMailboxIds())) {
                emailPatchObjectMapBuilder.put(email.getId(), Patches.set("mailboxIds", mailboxIds));
            }
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }

        final ListenableFuture<Boolean> patchesFuture = applyEmailPatches(
                patches,
                objectsState,
                inbox != null,
                multiCall
        );

        multiCall.execute();

        return Futures.transformAsync(patchesFuture, patchesResults -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            return Futures.immediateFuture(patchesResults);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> archive(final Collection<? extends IdentifiableEmailWithMailboxIds> emails) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole inbox = MailboxUtil.find(mailboxes, Role.INBOX);
            Preconditions.checkState(inbox != null, "Inbox mailbox not found. Calling archive (remove from inbox) on a collection of emails even though there is no inbox does not make sense");
            final IdentifiableMailboxWithRole archive = MailboxUtil.find(mailboxes, Role.ARCHIVE);
            return archive(emails, inbox, archive);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> archive(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                              @NonNullDecl final IdentifiableMailboxWithRole inbox,
                                              @NullableDecl final IdentifiableMailboxWithRole archive) {
        return Futures.transformAsync(
                getObjectsState(),
                objectsState -> archive(emails, inbox, archive, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> archive(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                              @NonNullDecl final IdentifiableMailboxWithRole inbox,
                                              @NullableDecl final IdentifiableMailboxWithRole archive,
                                              final ObjectsState objectsState) {
        Preconditions.checkNotNull(emails, "emails can not be null when attempting to archive them");

        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();

        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (archive == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.ARCHIVE, objectsState, multiCall);
        } else {
            mailboxCreateFuture = null;
        }

        ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithMailboxIds email : emails) {
            if (!email.getMailboxIds().containsKey(inbox.getId())) {
                continue;
            }
            Map<String, Boolean> mailboxIds = new HashMap<>(email.getMailboxIds());
            mailboxIds.remove(inbox.getId());
            if (archive == null) {
                mailboxIds.put(CreateUtil.createIdReference(Role.ARCHIVE), true);
            } else {
                mailboxIds.put(archive.getId(), true);
            }
            emailPatchObjectMapBuilder.put(email.getId(), Patches.set("mailboxIds", mailboxIds));
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }

        final ListenableFuture<Boolean> patchesFuture = applyEmailPatches(patches,
                objectsState,
                archive != null,
                multiCall
        );

        multiCall.execute();

        return Futures.transformAsync(patchesFuture, patchesResults -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            return Futures.immediateFuture(patchesResults);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> removeFromMailbox(Collection<? extends IdentifiableEmailWithMailboxIds> emails, @NonNullDecl Mailbox mailbox, @NullableDecl final IdentifiableMailboxWithRole archive) {
        Preconditions.checkNotNull(mailbox, "Mailbox can not be null when attempting to remove it from a collection of emails");
        if (archive != null) {
            Preconditions.checkArgument(archive.getRole() == Role.ARCHIVE, "Supplied archive mailbox must have the role ARCHIVE");
        }
        return removeFromMailbox(emails, mailbox.getId(), archive);
    }

    private ListenableFuture<Boolean> removeFromMailbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                        final String mailboxId,
                                                        @NullableDecl final IdentifiableMailboxWithRole archive) {
        return Futures.transformAsync(
                getObjectsState(),
                objectsState -> removeFromMailbox(emails, mailboxId, archive, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> removeFromMailbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                        final String mailboxId,
                                                        @NullableDecl final IdentifiableMailboxWithRole archive,
                                                        final ObjectsState objectsState) {
        Preconditions.checkNotNull(emails, "emails can not be null when attempting to remove them from a mailbox");
        Preconditions.checkNotNull(mailboxId, "mailboxId can not be null when attempting to remove emails");
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (archive == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.ARCHIVE, objectsState, multiCall);
        } else {
            mailboxCreateFuture = null;
        }
        ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithMailboxIds email : emails) {
            if (!email.getMailboxIds().containsKey(mailboxId)) {
                continue;
            }
            Map<String, Boolean> mailboxIds = new HashMap<>(email.getMailboxIds());
            mailboxIds.remove(mailboxId);
            if (mailboxIds.size() == 0) {
                if (archive == null) {
                    mailboxIds.put(CreateUtil.createIdReference(Role.ARCHIVE), true);
                } else {
                    mailboxIds.put(archive.getId(), true);
                }
            }
            emailPatchObjectMapBuilder.put(email.getId(), Patches.set("mailboxIds", mailboxIds));

        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }

        final ListenableFuture<Boolean> patchesFuture = applyEmailPatches(patches,
                objectsState,
                archive != null,
                multiCall
        );

        multiCall.execute();

        return Futures.transformAsync(patchesFuture, patchesResults -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            return Futures.immediateFuture(patchesResults);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> removeFromMailbox(final Collection<? extends IdentifiableEmailWithMailboxIds> emails, final String mailboxId) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole archive = MailboxUtil.find(mailboxes, Role.ARCHIVE);
            return removeFromMailbox(emails, mailboxId, archive);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> moveToTrash(final Collection<? extends IdentifiableEmailWithMailboxIds> emails) {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            return moveToTrash(emails, MailboxUtil.find(mailboxes, Role.TRASH));
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> moveToTrash(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                 @NullableDecl final IdentifiableMailboxWithRole trash) {
        return Futures.transformAsync(
                getObjectsState(),
                objectsState -> moveToTrash(emails, trash, objectsState),
                MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Boolean> moveToTrash(final Collection<? extends IdentifiableEmailWithMailboxIds> emails,
                                                  @NullableDecl final IdentifiableMailboxWithRole trash,
                                                  final ObjectsState objectsState) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<MethodResponses> mailboxCreateFuture;
        if (trash == null) {
            mailboxCreateFuture = getService(MailboxService.class).createMailbox(Role.TRASH, objectsState, multiCall);
        } else {
            mailboxCreateFuture = null;
        }
        ImmutableMap.Builder<String, Map<String, Object>> emailPatchObjectMapBuilder = ImmutableMap.builder();
        for (IdentifiableEmailWithMailboxIds email : emails) {
            if (trash != null && email.getMailboxIds().size() == 1 && email.getMailboxIds().containsKey(trash.getId())) {
                continue;
            }
            Patches.Builder patchesBuilder = Patches.builder();
            if (trash == null) {
                patchesBuilder.set("mailboxIds", ImmutableMap.of(CreateUtil.createIdReference(Role.TRASH), true));
            } else {
                patchesBuilder.set("mailboxIds", ImmutableMap.of(trash.getId(), true));
            }
            emailPatchObjectMapBuilder.put(email.getId(), patchesBuilder.build());
        }
        final ImmutableMap<String, Map<String, Object>> patches = emailPatchObjectMapBuilder.build();
        if (patches.size() == 0) {
            return Futures.immediateFuture(false);
        }
        final ListenableFuture<Boolean> patchesFuture = applyEmailPatches(patches,
                objectsState,
                trash != null,
                multiCall
        );
        multiCall.execute();
        return Futures.transformAsync(patchesFuture, patchesResults -> {
            if (mailboxCreateFuture != null) {
                SetMailboxMethodResponse setMailboxResponse = mailboxCreateFuture.get().getMain(SetMailboxMethodResponse.class);
                SetMailboxException.throwIfFailed(setMailboxResponse);
            }
            return Futures.immediateFuture(patchesResults);
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> emptyTrash() {
        return Futures.transformAsync(getService(MailboxService.class).getMailboxes(), mailboxes -> {
            Preconditions.checkNotNull(mailboxes, "SpecialMailboxes collection must not be null but can be empty");
            final IdentifiableMailboxWithRole trash = MailboxUtil.find(mailboxes, Role.TRASH);
            if (trash == null) {
                return Futures.immediateFailedFuture(new IllegalStateException("No mailbox with trash role"));
            }
            return emptyTrash(trash);
        }, MoreExecutors.directExecutor());

    }

    public ListenableFuture<Boolean> emptyTrash(@NonNullDecl IdentifiableMailboxWithRole trash) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final EmailFilterCondition filter = EmailFilterCondition.builder().inMailbox(trash.getId()).build();
        final JmapRequest.Call queryCall = multiCall.call(
                QueryEmailMethodCall.builder()
                        .accountId(accountId)
                        .filter(filter)
                        .build()
        );
        final ListenableFuture<MethodResponses> setFuture = multiCall.call(
                SetEmailMethodCall.builder()
                        .accountId(accountId)
                        .destroyReference(queryCall.createResultReference(Request.Invocation.ResultReference.Path.IDS))
                        .build()
        ).getMethodResponses();
        multiCall.execute();
        return Futures.transformAsync(setFuture, methodResponses -> {
            SetEmailMethodResponse setEmailMethodResponse = setFuture.get().getMain(SetEmailMethodResponse.class);
            SetEmailException.throwIfFailed(setEmailMethodResponse);
            final String[] destroyed = setEmailMethodResponse.getDestroyed();
            LOGGER.info("Deleted {} emails", destroyed == null ? 0 : destroyed.length);
            return Futures.immediateFuture(true);
        }, MoreExecutors.directExecutor());
    }
}