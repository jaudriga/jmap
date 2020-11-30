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
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mock.server.JmapDispatcher;
import rs.ltt.jmap.mock.server.MockMailServer;
import rs.ltt.jmap.mua.service.exception.PreexistingMailboxException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ExistingArchiveNoRoleTest {

    @Test
    public void queryAndRemoveFromInbox() throws ExecutionException, InterruptedException, IOException {
        final MockWebServer server = new MockWebServer();
        final MockMailServer mailServer = new PreexistingArchiveMockMailServer(2);
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
            final List<CachedEmail> threadT1 = cache.getEmails("T1");
            final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class,()->{
                mua.archive(threadT1).get();
            });
            MatcherAssert.assertThat(executionException.getCause(), CoreMatchers.instanceOf(PreexistingMailboxException.class));
        } finally {
            server.shutdown();
        }
    }

    private static class PreexistingArchiveMockMailServer extends MockMailServer {

        public PreexistingArchiveMockMailServer(int numThreads) {
            super(numThreads);
        }

        @Override
        protected List<MailboxInfo> generateMailboxes() {
            return Arrays.asList(
                    new MailboxInfo(UUID.randomUUID().toString(), "Inbox", Role.INBOX),
                    new MailboxInfo(UUID.randomUUID().toString(), "Archive", null)
            );
        }
    }
}
