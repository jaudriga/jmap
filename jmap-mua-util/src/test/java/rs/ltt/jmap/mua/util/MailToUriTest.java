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

package rs.ltt.jmap.mua.util;

import org.junit.Assert;
import org.junit.Test;
import rs.ltt.jmap.common.entity.EmailAddress;

public class MailToUriTest {

    private static final String EXAMPLE_SUBJECT = "Foo";
    private static final String EXAMPLE_ADDRESS_ALPHA = "alpha@example.com";
    private static final String EXAMPLE_ADDRESS_BETA = "beta@example.com";
    private static final String EXAMPLE_ADDRESS_GAMMA = "gamma@example.com";
    private static final String EXAMPLE_ADDRESS_DELTA = "delta@example.com";

    @Test
    public void standaloneEmail() {
        Assert.assertEquals(
                MailToUri.builder()
                        .to(EmailAddress.builder().email(EXAMPLE_ADDRESS_ALPHA).build())
                        .build(),
                MailToUri.parse(String.format("mailto:%s", EXAMPLE_ADDRESS_ALPHA))
        );
    }

    @Test
    public void emailAndSubject() {
        Assert.assertEquals(
                MailToUri.builder()
                        .to(EmailAddress.builder().email(EXAMPLE_ADDRESS_ALPHA).build())
                        .subject(EXAMPLE_SUBJECT)
                        .build(),
                MailToUri.parse(String.format("mailto:%s?subject=%s", EXAMPLE_ADDRESS_ALPHA, EXAMPLE_SUBJECT))
        );
    }

    @Test
    public void emptyToAndSubject() {
        Assert.assertEquals(
                MailToUri.builder()
                        .subject(EXAMPLE_SUBJECT)
                        .build(),
                MailToUri.parse(String.format("mailto:?subject=%s", EXAMPLE_SUBJECT))
        );
    }

    @Test
    public void withCcAndBcc() {
        Assert.assertEquals(
                MailToUri.builder()
                        .to(EmailAddress.builder().email(EXAMPLE_ADDRESS_ALPHA).build())
                        .cc(EmailAddress.builder().email(EXAMPLE_ADDRESS_BETA).build())
                        .cc(EmailAddress.builder().email(EXAMPLE_ADDRESS_GAMMA).build())
                        .bcc((EmailAddress.builder().email(EXAMPLE_ADDRESS_DELTA).build()))
                        .subject(EXAMPLE_SUBJECT)
                        .build(),
                MailToUri.parse(String.format(
                        "mailto:%s?cc=%s,%s&bcc=%s&subject=%s",
                        EXAMPLE_ADDRESS_ALPHA,
                        EXAMPLE_ADDRESS_BETA,
                        EXAMPLE_ADDRESS_GAMMA,
                        EXAMPLE_ADDRESS_DELTA,
                        EXAMPLE_SUBJECT)
                )
        );
    }

    @Test
    public void withInReplyTo() {
        Assert.assertEquals(
                MailToUri.builder()
                        .to(EmailAddress.builder().email("list@example.com").build())
                        .inReplyTo("<3469A91.D10AF4C@example.com>")
                        .build(),
                MailToUri.parse("mailto:list@example.com?In-Reply-To=%3C3469A91.D10AF4C@example.com%3E")
        );
    }
}
