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
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.filter.FilterOperator;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mock.server.JmapDispatcher;
import rs.ltt.jmap.mock.server.MockMailServer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class MockMailServerTest {


    @Test
    public void queryRefreshQuery() throws ExecutionException, InterruptedException, IOException {
        final MockWebServer server = new MockWebServer();
        server.setDispatcher(new MockMailServer(128));

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
}
