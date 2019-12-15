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

package rs.ltt.jmap.mua.util;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;
import rs.ltt.jmap.common.entity.EmailAddress;

import java.util.Collection;

public class EmailAddressUtilTest {

    @Test
    public void twoEmailAddressesWithLabel() {
        final String input = "\"Last, First\" <first.last@example.com>, Test <test@example.com>";
        final Collection<EmailAddress> expected = ImmutableList.of(
                EmailAddress.builder().email("first.last@example.com").name("Last, First").build(),
                EmailAddress.builder().email("test@example.com").name("Test").build()
        );
        final Collection<EmailAddress> actual = EmailAddressUtil.parse(input);
        Assert.assertArrayEquals(
                expected.toArray(new EmailAddress[0]),
                actual.toArray(new EmailAddress[0])
        );
    }

    @Test
    public void validEmailAddresses() {
        //copied from https://en.wikipedia.org/wiki/Email_address#Examples
        Assert.assertTrue(EmailAddressUtil.isValid("simple@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("very.common@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("disposable.style.email.with+symbol@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("other.email-with-hyphen@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("fully-qualified-domain@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("user.name+tag+sorting@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("x@example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("example-indeed@strange-example.com"));
        Assert.assertTrue(EmailAddressUtil.isValid("admin@mailserver1"));
        Assert.assertTrue(EmailAddressUtil.isValid("example@s.example"));
        Assert.assertTrue(EmailAddressUtil.isValid("\" \"@example.org"));
        Assert.assertTrue(EmailAddressUtil.isValid("\"john..doe\"@example.org"));
        Assert.assertTrue(EmailAddressUtil.isValid("mailhost!username@example.org"));
        Assert.assertTrue(EmailAddressUtil.isValid("user%example.com@example.org"));
    }

    @Test
    public void invalidEmailAddresses() {
        //copied from https://en.wikipedia.org/wiki/Email_address#Examples
        Assert.assertFalse(EmailAddressUtil.isValid("Abc.example.com"));
        Assert.assertFalse(EmailAddressUtil.isValid("A@b@c@example.com"));
        Assert.assertFalse(EmailAddressUtil.isValid("a\"b(c)d,e:f;g<h>i[j\\k]l@example.com"));
        Assert.assertFalse(EmailAddressUtil.isValid("just\"not\"right@example.com"));
        Assert.assertFalse(EmailAddressUtil.isValid("this is\"not\\allowed@example.com"));
        Assert.assertFalse(EmailAddressUtil.isValid("this\\ still\\\"not\\\\allowed@example.com"));
    }
}
