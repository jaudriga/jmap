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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import rs.ltt.jmap.common.entity.EmailAddress;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Builder
@Getter
@ToString
@EqualsAndHashCode
public class MailToUri {

    private static final String MAIL_TO = "mailto";
    private static final String TO = "to";
    private static final String CC = "cc";
    private static final String BCC = "bcc";
    private static final String IN_REPLY_TO = "in-reply-to";
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";

    @Singular("to")
    private final Collection<EmailAddress> to;
    @Singular("cc")
    private final Collection<EmailAddress> cc;
    @Singular("bcc")
    private final Collection<EmailAddress> bcc;
    private final String inReplyTo;
    private final String subject;
    private final String body;


    public static MailToUri parse(final String input) throws IllegalArgumentException {
        final int schemaDelimiter = input.indexOf(":");
        if (schemaDelimiter < 0) {
            throw new IllegalArgumentException();
        }
        if (input.substring(0, schemaDelimiter).equals(MAIL_TO)) {
            final int queryDelimiter = input.length() > schemaDelimiter ? input.indexOf("?", schemaDelimiter + 1) : -1;
            final String to;
            final String query;
            if (queryDelimiter > 0) {
                to = input.substring(schemaDelimiter + 1, queryDelimiter);
                query = input.substring(queryDelimiter + 1);
            } else {
                to = input.substring(schemaDelimiter + 1);
                query = null;
            }
            final Map<String, String> parameters = parseQuery(query);
            final String cc = parameters.get(CC);
            final String bcc = parameters.get(BCC);
            final String inReplyTo = parameters.get(IN_REPLY_TO);
            final String subject = parameters.get(SUBJECT);
            final String body = parameters.get(BODY);
            final MailToUriBuilder mailToUriBuilder = MailToUri.builder();
            if (Strings.isNullOrEmpty(to)) {
                final String toParameter = parameters.get(TO);
                if (toParameter != null) {
                    mailToUriBuilder.to(EmailAddressUtil.parseAddresses(toParameter));
                }
            } else {
                mailToUriBuilder.to(EmailAddressUtil.parseAddresses(to));
            }
            if (cc != null) {
                mailToUriBuilder.cc(EmailAddressUtil.parseAddresses(cc));
            }
            if (bcc != null) {
                mailToUriBuilder.bcc(EmailAddressUtil.parseAddresses(bcc));
            }
            mailToUriBuilder.inReplyTo(inReplyTo);
            mailToUriBuilder.subject(subject);
            mailToUriBuilder.body(body);
            return mailToUriBuilder.build();

        }
        throw new IllegalArgumentException("Unknown scheme");
    }

    private static Map<String, String> parseQuery(final String query) {
        if (query == null) {
            return Collections.emptyMap();
        }
        final ImmutableMap.Builder<String, String> mapBuilder = new ImmutableMap.Builder<>();
        for (final String parameter : query.split("&")) {
            final String[] parts = parameter.split("=", 2);
            if (parts.length == 2) {
                mapBuilder.put(parts[0].toLowerCase(Locale.ENGLISH), decode(parts[1]));
            }
        }
        return mapBuilder.build();
    }

    private static String decode(String input) {
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

}
