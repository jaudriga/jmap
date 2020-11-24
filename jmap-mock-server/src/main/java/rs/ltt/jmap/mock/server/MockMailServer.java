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

package rs.ltt.jmap.mock.server;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import rs.ltt.jmap.common.Request;
import rs.ltt.jmap.common.Response;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.Thread;
import rs.ltt.jmap.common.method.MethodResponse;
import rs.ltt.jmap.common.method.call.email.ChangesEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.GetEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryChangesEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryEmailMethodCall;
import rs.ltt.jmap.common.method.call.mailbox.ChangesMailboxMethodCall;
import rs.ltt.jmap.common.method.call.mailbox.GetMailboxMethodCall;
import rs.ltt.jmap.common.method.call.thread.ChangesThreadMethodCall;
import rs.ltt.jmap.common.method.call.thread.GetThreadMethodCall;
import rs.ltt.jmap.common.method.error.CannotCalculateChangesMethodErrorResponse;
import rs.ltt.jmap.common.method.error.InvalidResultReferenceMethodErrorResponse;
import rs.ltt.jmap.common.method.error.UnknownMethodMethodErrorResponse;
import rs.ltt.jmap.common.method.response.email.ChangesEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.GetEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.QueryChangesEmailMethodResponse;
import rs.ltt.jmap.common.method.response.email.QueryEmailMethodResponse;
import rs.ltt.jmap.common.method.response.mailbox.ChangesMailboxMethodResponse;
import rs.ltt.jmap.common.method.response.mailbox.GetMailboxMethodResponse;
import rs.ltt.jmap.common.method.response.thread.ChangesThreadMethodResponse;
import rs.ltt.jmap.common.method.response.thread.GetThreadMethodResponse;

import java.util.*;
import java.util.stream.Collectors;

public class MockMailServer extends StubMailServer {
    protected final Map<String, Email> emails = new HashMap<>();
    protected final Map<String, MailboxInfo> mailboxes = new HashMap<>();

    private int state = 0;

    public MockMailServer(int numThreads) {
        setup(numThreads);
    }

    protected void setup(int numThreads) {
        final String inboxId = "0";
        this.mailboxes.put(inboxId, new MailboxInfo("Inbox", Role.INBOX));
        generateEmail(inboxId, numThreads);
    }

    private void generateEmail(final String mailboxId, final int numThreads) {
        int emailCount = 0;
        for (int thread = 0; thread < numThreads; ++thread) {
            final int numInThread = (thread % 4) + 1;
            for (int i = 0; i < numInThread; ++i) {
                final Email email = EmailGenerator.get(mailboxId, emailCount, thread, i, numInThread);
                this.emails.put(email.getId(), email);
                emailCount++;
            }
        }
    }

    protected void incrementState() {
        this.state++;
    }

    @Override
    protected MethodResponse[] execute(ChangesEmailMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final String since = methodCall.getSinceState();
        if (since != null && since.equals(getState())) {
            return new MethodResponse[]{
                    ChangesEmailMethodResponse.builder()
                            .oldState(getState())
                            .newState(getState())
                            .updated(new String[0])
                            .created(new String[0])
                            .destroyed(new String[0])
                            .build()
            };
        } else {
            return new MethodResponse[]{new CannotCalculateChangesMethodErrorResponse()};
        }
    }

    @Override
    protected MethodResponse[] execute(GetEmailMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final Request.Invocation.ResultReference idsReference = methodCall.getIdsReference();
        final List<String> ids;
        if (idsReference != null) {
            try {
                ids = Arrays.asList(ResultReferenceResolver.resolve(idsReference, previousResponses));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return new MethodResponse[]{new InvalidResultReferenceMethodErrorResponse()};
            }
        } else {
            ids = Arrays.asList(methodCall.getIds());
        }
        //TODO look at properties and return only threadIds if needed
        System.out.println("ids: " + Arrays.asList(ids));
        return new MethodResponse[]{
                GetEmailMethodResponse.builder()
                        .list(ids.stream().map(emails::get).toArray(Email[]::new))
                        .state(getState())
                        .build()
        };
    }

    protected String getState() {
        return String.valueOf(this.state);
    }

    @Override
    protected MethodResponse[] execute(QueryEmailMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final ArrayList<Email> temporaryList = new ArrayList<>(emails.values());
        temporaryList.sort(Comparator.comparing(Email::getReceivedAt).reversed());
        System.out.println(methodCall.getFilter());
        final HashSet<String> threadIds = new HashSet<>();
        temporaryList.removeIf(email -> !threadIds.add(email.getThreadId()));
        return new MethodResponse[]{
                QueryEmailMethodResponse.builder()
                        .canCalculateChanges(true) //instructing the client to use QueryChanges on the next attempt even though we can’t
                        .queryState(getState())
                        .ids(Collections2.transform(temporaryList, Email::getId).toArray(new String[0]))
                        .position(0L)
                        .build()
        };
    }

    @Override
    protected MethodResponse[] execute(QueryChangesEmailMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final String since = methodCall.getSinceQueryState();
        if (since != null && since.equals(getState())) {
            return new MethodResponse[]{
                    QueryChangesEmailMethodResponse.builder()
                            .oldQueryState(getState())
                            .newQueryState(getState())
                            .added(Collections.emptyList())
                            .removed(new String[0])
                            .build()
            };
        } else {
            return new MethodResponse[]{new CannotCalculateChangesMethodErrorResponse()};
        }
    }

    @Override
    protected MethodResponse[] execute(GetMailboxMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final ImmutableList.Builder<Mailbox> mailboxListBuilder = new ImmutableList.Builder<>();
        for (final Map.Entry<String, MailboxInfo> entry : mailboxes.entrySet()) {
            final String id = entry.getKey();
            final MailboxInfo mailboxInfo = entry.getValue();
            mailboxListBuilder.add(Mailbox.builder()
                    .id(id)
                    .name(mailboxInfo.name)
                    .role(mailboxInfo.role)
                    .build());
        }
        return new MethodResponse[]{
                GetMailboxMethodResponse.builder()
                        .list(mailboxListBuilder.build().toArray(new Mailbox[0]))
                        .state(getState())
                        .build()
        };
    }

    @Override
    protected MethodResponse[] execute(ChangesMailboxMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final String since = methodCall.getSinceState();
        if (since != null && since.equals(getState())) {
            return new MethodResponse[]{
                    ChangesMailboxMethodResponse.builder()
                            .oldState(getState())
                            .newState(getState())
                            .updated(new String[0])
                            .created(new String[0])
                            .destroyed(new String[0])
                            .updatedProperties(new String[0])
                            .build()
            };
        } else {
            return new MethodResponse[]{new CannotCalculateChangesMethodErrorResponse()};
        }
    }

    @Override
    protected MethodResponse[] execute(GetThreadMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final Request.Invocation.ResultReference idsReference = methodCall.getIdsReference();
        final List<String> ids;
        if (idsReference != null) {
            try {
                ids = Arrays.asList(ResultReferenceResolver.resolve(idsReference, previousResponses));
            } catch (final IllegalArgumentException e) {
                return new MethodResponse[]{new InvalidResultReferenceMethodErrorResponse()};
            }
        } else {
            ids = Arrays.asList(methodCall.getIds());
        }
        System.out.println("thread ids " + ids);
        Thread[] threads = ids.stream()
                .map(threadId -> Thread.builder()
                        .id(threadId)
                        .emailIds(emails.values().stream()
                                .filter(email -> email.getThreadId().equals(threadId))
                                .sorted(Comparator.comparing(Email::getReceivedAt))
                                .map(Email::getId).collect(Collectors.toList())).build())
                .toArray(Thread[]::new);
        return new MethodResponse[]{
                GetThreadMethodResponse.builder()
                        .list(threads)
                        .state(getState())
                        .build()
        };
    }

    @Override
    protected MethodResponse[] execute(ChangesThreadMethodCall methodCall, ListMultimap<String, Response.Invocation> previousResponses) {
        final String since = methodCall.getSinceState();
        if (since != null && since.equals(getState())) {
            return new MethodResponse[]{
                    ChangesThreadMethodResponse.builder()
                            .oldState(getState())
                            .newState(getState())
                            .updated(new String[0])
                            .created(new String[0])
                            .destroyed(new String[0])
                            .build()
            };
        } else {
            return new MethodResponse[]{new CannotCalculateChangesMethodErrorResponse()};
        }
    }

    protected static class MailboxInfo {
        public final String name;
        public final Role role;

        public MailboxInfo(String name, Role role) {
            this.name = name;
            this.role = role;
        }
    }

}
