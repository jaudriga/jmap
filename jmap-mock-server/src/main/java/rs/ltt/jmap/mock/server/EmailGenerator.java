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

import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.EmailAddress;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EmailGenerator {

    private static final ZoneOffset DEFAULT_ZONE_OFFSET = ZoneOffset.ofHours(1);

    private static final Instant START_DATE = Instant.ofEpochSecond(1605800000);

    private static final List<String> SUBJECT_TEMPLATES = Arrays.asList(
            "Lorem ipsum dolor sit",
            "Maecenas eleifend rhoncus",
            "Vivamus in convallis",
            "Maecenas varius egestas",
            "Sed sit amet fringilla"
    );

    public static Email get(int index, int thread, int posInThread, int numInThread) {
        final Instant receivedAt = START_DATE.minus(Duration.ofDays(thread)).plus(Duration.ofHours(posInThread));
        final EmailAddress from = getEmailAddress(index);
        final ArrayList<EmailAddress> recipients = new ArrayList<>();
        final int threadStartsAt = index - posInThread;
        final int threadEndsAt = threadStartsAt + numInThread;
        for(int i = threadStartsAt; i < threadEndsAt; ++i) {
            if (i != index) {
                recipients.add(getEmailAddress(i));
            }
        }
        final String id = String.format("M%d", index);
        final String threadId = String.format("T%d", thread);
        final String subject = String.format(SUBJECT_TEMPLATES.get(thread % SUBJECT_TEMPLATES.size()), thread);
        //System.out.println(subject+", Date "+receivedAt+" From: "+from+", Recipients: "+recipients);
        return Email.builder()
                .id(id)
                .threadId(threadId)
                .receivedAt(receivedAt)
                .sentAt(receivedAt.atOffset(DEFAULT_ZONE_OFFSET))
                .to(recipients)
                .from(from)
                .subject(subject)
                .build();
    }

    private static EmailAddress getEmailAddress(int index) {
        final NameGenerator.Name name = NameGenerator.get(index);
        return EmailAddress.builder()
                .email(name.first.toLowerCase(Locale.ENGLISH)+"."+name.last.toLowerCase(Locale.ENGLISH)+"@example.com")
                .name(name.first+" "+name.last)
                .build();

    }

}
