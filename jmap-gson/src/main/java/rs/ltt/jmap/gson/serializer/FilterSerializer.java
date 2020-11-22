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

package rs.ltt.jmap.gson.serializer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import rs.ltt.jmap.common.entity.AbstractIdentifiableEntity;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.EmailSubmission;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.filter.Filter;

import java.lang.reflect.Type;

/**
 * TODO: This filter currently uses hard coded elements. We will eventually have to use an annotation processor or something
 *  similar to automatically generate this.
 *  It is probably possible to have some sort of @JmapEntity(filterCondition=EntityFilterCondition.class) annotation
 *  Having the class of the full filter condition will be relevant for the FilterDeserializer
 */
public class FilterSerializer implements JsonSerializer<Filter<? extends AbstractIdentifiableEntity>> {

    public static final Type EMAIL_FILTER_TYPE =  new TypeToken<Filter<Email>>() {

    }.getType();

    public static final Type EMAIL_SUBMISSION_FILTER_TYPE =  new TypeToken<Filter<EmailSubmission>>() {

    }.getType();

    public static final Type MAILBOX_FILTER_TYPE =  new TypeToken<Filter<Mailbox>>() {

    }.getType();

    public static void register(final GsonBuilder builder) {
        builder.registerTypeAdapter(EMAIL_FILTER_TYPE, new FilterSerializer());
        builder.registerTypeAdapter(EMAIL_SUBMISSION_FILTER_TYPE, new FilterSerializer());
        builder.registerTypeAdapter(MAILBOX_FILTER_TYPE, new FilterSerializer());
    }

    @Override
    public JsonElement serialize(Filter<? extends AbstractIdentifiableEntity> filter, Type type, JsonSerializationContext context) {
        return context.serialize(filter);
    }
}
