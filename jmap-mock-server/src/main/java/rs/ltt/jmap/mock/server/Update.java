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

import com.google.common.collect.ImmutableMap;
import rs.ltt.jmap.common.entity.AbstractIdentifiableEntity;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.Thread;

import java.util.Collection;
import java.util.Map;

public class Update {

    private final Map<Class<? extends AbstractIdentifiableEntity>, Changes> changes;

    private final String newVersion;


    private Update(Map<Class<? extends AbstractIdentifiableEntity>, Changes> changes, String newVersion) {
        this.changes = changes;
        this.newVersion = newVersion;
    }

    public static Update created(Email email, String newVersion) {
        final ImmutableMap.Builder<Class<? extends AbstractIdentifiableEntity>, Changes> builder = new ImmutableMap.Builder<>();
        builder.put(Email.class, new Changes(new String[0], new String[]{email.getId()}));
        builder.put(Thread.class, new Changes(new String[0], new String[]{email.getThreadId()}));
        builder.put(Mailbox.class, new Changes(email.getMailboxIds().keySet().toArray(new String[0]), new String[0]));
        return new Update(builder.build(), newVersion);
    }

    public static Update updated(final Collection<Email> emails, String newVersion) {
        final ImmutableMap.Builder<Class<? extends AbstractIdentifiableEntity>, Changes> builder = new ImmutableMap.Builder<>();
        builder.put(Email.class, new Changes(emails.stream().map(Email::getId).toArray(String[]::new), new String[0]));
        builder.put(Thread.class, new Changes(new String[0], new String[0]));
        builder.put(Mailbox.class, new Changes(emails.stream().map(email -> email.getMailboxIds().keySet()).flatMap(Collection::stream).distinct().toArray(String[]::new), new String[0]));
        return new Update(builder.build(), newVersion);
    }

    public Changes getChangesFor(final Class<? extends AbstractIdentifiableEntity> clazz) {
        return changes.get(clazz);
    }

    public String getNewVersion() {
        return newVersion;
    }
}
