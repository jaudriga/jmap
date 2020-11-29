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

package rs.ltt.jmap.mua;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.entity.*;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.filter.FilterOperator;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mock.server.JmapDispatcher;
import rs.ltt.jmap.mock.server.MockMailServer;
import rs.ltt.jmap.mua.cache.InMemoryCache;
import rs.ltt.jmap.mua.util.MailboxUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class MockMailServerTest {


    @Test
    public void queryRefreshQuery() throws ExecutionException, InterruptedException, IOException {
        final MockWebServer server = new MockWebServer();
        final MockMailServer mailServer = new MockMailServer(128);
        //this will make the second query() a queryChangesCall
        mailServer.setReportCanCalculateQueryChanges(true);
        server.setDispatcher(mailServer);

        try (final Mua mua = Mua.builder()
                .sessionResource(server.url(JmapDispatcher.WELL_KNOWN_PATH))
                .username(JmapDispatcher.USERNAME)
                .password(JmapDispatcher.PASSWORD)
                .accountId(JmapDispatcher.ACCOUNT_ID)
                .build()) {
            final EmailQuery query = EmailQuery.of(
                    FilterOperator.or(
                            EmailFilterCondition.builder().inMailbox("0").build(),
                            EmailFilterCondition.builder().inMailbox("1").build()
                    ),
                    true
            );
            mua.query(query).get();
            mua.refresh().get();
            mua.query(query).get();
        }
        server.shutdown();
    }

    @Test
    public void addEmailAndRefresh() throws ExecutionException, InterruptedException, IOException {
        final MockWebServer server = new MockWebServer();
        final MockMailServer mailServer = new MockMailServer(128);
        server.setDispatcher(mailServer);
        final MyInMemoryCache cache = new MyInMemoryCache();
        try (final Mua mua = Mua.builder()
                .cache(cache)
                .sessionResource(server.url(JmapDispatcher.WELL_KNOWN_PATH))
                .username(JmapDispatcher.USERNAME)
                .password(JmapDispatcher.PASSWORD)
                .accountId(JmapDispatcher.ACCOUNT_ID)
                .build()) {
            mua.query(EmailQuery.unfiltered()).get();
            final Email email = mailServer.generateEmailOnTop();
            final Status status = mua.refresh().get();
            Assertions.assertEquals(Status.UPDATED, status);
            Assertions.assertTrue(cache.getEmailIds().contains(email.getId()), "new email id not found in cache");
            Assertions.assertTrue(cache.getThreadIds().contains(email.getThreadId()), "new thread id not found in cache");
        }
        server.shutdown();
    }

    @Test
    public void queryAndMarkAsRead() throws IOException, ExecutionException, InterruptedException {
        final MockWebServer server = new MockWebServer();
        final MockMailServer mailServer = new MockMailServer(2);
        server.setDispatcher(mailServer);
        final MyInMemoryCache cache = new MyInMemoryCache();
        try (final Mua mua = Mua.builder()
                .cache(cache)
                .sessionResource(server.url(JmapDispatcher.WELL_KNOWN_PATH))
                .username(JmapDispatcher.USERNAME)
                .password(JmapDispatcher.PASSWORD)
                .accountId(JmapDispatcher.ACCOUNT_ID)
                .build()) {
            mua.query(EmailQuery.unfiltered()).get();
            final Mailbox mailboxBeforeModification = cache.getMailbox(Role.INBOX);
            Assertions.assertEquals(2, mailboxBeforeModification.getUnreadThreads(), "Miss match in unread threads");
            Assertions.assertEquals(3, mailboxBeforeModification.getUnreadEmails(), "Miss match in unread emails");
            final List<CachedEmail> emails = cache.getEmails("T1");
            mua.setKeyword(emails, Keyword.SEEN).get();

            mua.refresh().get();

            final Mailbox mailboxAfterModification = cache.getMailbox(Role.INBOX);
            Assertions.assertEquals(
                    1,
                    mailboxAfterModification.getUnreadThreads(),
                    "Miss match in unread thread after modification"
            );
            Assertions.assertEquals(
                    1,
                    mailboxAfterModification.getUnreadEmails(),
                    "Miss match in unread email after modification"
            );
        }
        server.shutdown();
    }

    @Test
    public void queryAndRemoveFromInbox() throws ExecutionException, InterruptedException {
        final MockWebServer server = new MockWebServer();
        final MockMailServer mailServer = new MockMailServer(2);
        server.setDispatcher(mailServer);
        final MyInMemoryCache cache = new MyInMemoryCache();
        try (final Mua mua = Mua.builder()
                .cache(cache)
                .sessionResource(server.url(JmapDispatcher.WELL_KNOWN_PATH))
                .username(JmapDispatcher.USERNAME)
                .password(JmapDispatcher.PASSWORD)
                .accountId(JmapDispatcher.ACCOUNT_ID)
                .build()) {
            mua.query(EmailQuery.unfiltered()).get();
            final List<CachedEmail> emails = cache.getEmails("T1");
            mua.archive(emails).get();
            //creating the archive mailbox and adding email are two steps / two versions
            Assertions.assertEquals(Status.HAS_MORE, mua.refresh().get());
            Assertions.assertEquals(Status.UPDATED, mua.refresh().get());

            final Mailbox inboxAfterModification = cache.getMailbox(Role.INBOX);
            final Mailbox archiveAfterModification = cache.getMailbox(Role.ARCHIVE);

            Assertions.assertNotNull(archiveAfterModification);

            Assertions.assertEquals(1, archiveAfterModification.getUnreadThreads());
            Assertions.assertEquals(1, inboxAfterModification.getUnreadThreads());

            Assertions.assertEquals(2, archiveAfterModification.getTotalEmails());
            Assertions.assertEquals(1, inboxAfterModification.getTotalEmails());
        }
    }

    private static class MyInMemoryCache extends InMemoryCache {
        public Collection<String> getEmailIds() {
            return emails.keySet();
        }

        public Collection<String> getThreadIds() {
            return threads.keySet();
        }

        private List<CachedEmail> getEmails(final String threadId) {
            List<String> emailIds = this.threads.get(threadId).getEmailIds();
            return emailIds.stream().map(id -> new CachedEmail(emails.get(id))).collect(Collectors.toList());
        }

        public Mailbox getMailbox(final Role role) {
            return (Mailbox) MailboxUtil.find(this.mailboxes.values(), role);
        }
    }

    private static class CachedEmail implements IdentifiableEmailWithKeywords, IdentifiableEmailWithMailboxIds {

        private final Email inner;

        private CachedEmail(Email inner) {
            this.inner = inner;
        }

        @Override
        public Map<String, Boolean> getKeywords() {
            final Map<String, Boolean> keywords = inner.getKeywords();
            return keywords == null ? Collections.emptyMap() : keywords;
        }

        @Override
        public String getId() {
            return inner.getId();
        }

        @Override
        public Map<String, Boolean> getMailboxIds() {
            final Map<String, Boolean> mailboxIds = inner.getMailboxIds();
            return mailboxIds == null ? Collections.emptyMap() : mailboxIds;
        }
    }
}
