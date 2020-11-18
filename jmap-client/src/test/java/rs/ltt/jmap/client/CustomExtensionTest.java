/*
 * Copyright 2019 Daniel Gultsch
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

package rs.ltt.jmap.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.method.call.core.EchoMethodCall;
import rs.ltt.jmap.common.method.response.core.EchoMethodResponse;
import rs.ltt.jmap.common.util.Mapper;

import java.util.concurrent.ExecutionException;

public class CustomExtensionTest {

    private static String ACCOUNT_ID = "test@example.com";
    private static String USERNAME = "test@example.com";
    private static String PASSWORD = "secret";

    @Test
    public void findDummyAndCommonMethodCalls() {
        Assertions.assertTrue(Mapper.METHOD_CALLS.values().contains(GetDummyMethodCall.class));
        Assertions.assertTrue(Mapper.METHOD_CALLS.values().contains(EchoMethodCall.class));
    }

    @Test
    public void findDummyAndCommonMethodResponses() {
        Assertions.assertTrue(Mapper.METHOD_RESPONSES.values().contains(GetDummyMethodResponse.class));
        Assertions.assertTrue(Mapper.METHOD_RESPONSES.values().contains(EchoMethodResponse.class));
    }

    @Test
    public void failOnCallWithoutNamespace() throws ExecutionException, InterruptedException {
        final JmapClient client = new JmapClient(USERNAME, PASSWORD);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            client.call(new GetDummyMethodCall(ACCOUNT_ID)).get();
        });

    }
}
