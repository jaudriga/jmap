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

package rs.ltt.jmap.common;

import org.junit.Test;
import rs.ltt.jmap.common.method.call.email.GetEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryEmailMethodCall;
import rs.ltt.jmap.common.method.call.mailbox.ChangesMailboxMethodCall;
import rs.ltt.jmap.common.method.call.mailbox.GetMailboxMethodCall;
import rs.ltt.jmap.common.method.call.snippet.GetSearchSnippetsMethodCall;

public class MethodCallTest {

    @Test(expected = NullPointerException.class)
    public void isAccountIdRequiredInGetMailboxMethodCall() {
        GetMailboxMethodCall.builder().build();
    }

    @Test(expected = NullPointerException.class)
    public void isAccountIdRequiredInChangesMailboxMethodCall() {
        ChangesMailboxMethodCall.builder().sinceState("dummy").build();
    }

    @Test(expected = NullPointerException.class)
    public void isSinceStateRequiredInChangesMailboxMethodCall() {
        ChangesMailboxMethodCall.builder().accountId("dummy").build();
    }

    @Test(expected = NullPointerException.class)
    public void isAccountIdRequiredInGetSearchSnippetsMethodCall() {
        GetSearchSnippetsMethodCall.builder()
                .emailIds(new String[]{"1", "2"})
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void isOneOfEmailIdRequiredInGetSearchSnippetsMethodCall() {
        GetSearchSnippetsMethodCall.builder()
                .accountId("dummy")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void preventReferenceAndIdInGetEmailMethodCall() {
        final Request.Invocation invocation = new Request.Invocation(
                QueryEmailMethodCall.builder()
                        .accountId("dummy")
                        .build(),
                "1"
        );
        GetEmailMethodCall.builder()
                .accountId("dummy")
                .ids(new String[]{"1", "2"})
                .idsReference(
                        invocation.createReference(
                                Request.Invocation.ResultReference.Path.LIST_EMAIL_IDS
                        ))
                .build();
    }
}
