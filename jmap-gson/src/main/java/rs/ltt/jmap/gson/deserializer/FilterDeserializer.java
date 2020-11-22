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

package rs.ltt.jmap.gson.deserializer;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import rs.ltt.jmap.common.entity.AbstractIdentifiableEntity;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.EmailSubmission;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.filter.*;

import java.lang.reflect.Type;

import static rs.ltt.jmap.gson.serializer.FilterSerializer.*;

/**
 * TODO: This class currently uses hard coded elements. This limits extensibility of this library. See Comments in FilterSerializer
 *  for some ideas on how to circumvent this in the future.
 */
public class FilterDeserializer implements JsonDeserializer<Filter<? extends AbstractIdentifiableEntity>> {

    private static final Type EMAIL_FILTER__OPERATOR_TYPE = new TypeToken<FilterOperator<Email>>() {

    }.getType();

    private static final Type EMAIL_SUBMISSION_FILTER__OPERATOR_TYPE = new TypeToken<FilterOperator<EmailSubmission>>() {

    }.getType();

    private static final Type MAILBOX_FILTER__OPERATOR_TYPE = new TypeToken<FilterOperator<Mailbox>>() {

    }.getType();

    public static void register(final GsonBuilder builder) {
        builder.registerTypeAdapter(EMAIL_FILTER_TYPE, new FilterDeserializer());
        builder.registerTypeAdapter(EMAIL_SUBMISSION_FILTER_TYPE, new FilterDeserializer());
        builder.registerTypeAdapter(MAILBOX_FILTER_TYPE, new FilterDeserializer());
    }

    @Override
    public Filter<? extends AbstractIdentifiableEntity> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject();
        final boolean isOperator = jsonObject.has("operator") && jsonObject.has("conditions");
        if (type.equals(EMAIL_FILTER_TYPE)) {
            if (isOperator) {
                return context.deserialize(jsonElement, EMAIL_FILTER__OPERATOR_TYPE);
            } else {
                return context.deserialize(jsonElement, EmailFilterCondition.class);
            }

        }
        if (type.equals(EMAIL_SUBMISSION_FILTER_TYPE)) {
            if (isOperator) {
                return context.deserialize(jsonElement, EMAIL_SUBMISSION_FILTER__OPERATOR_TYPE);
            } else {
                return context.deserialize(jsonElement, EmailSubmissionFilterCondition.class);
            }
        }
        if (type.equals(MAILBOX_FILTER_TYPE)) {
            if (isOperator) {
                return context.deserialize(jsonElement, MAILBOX_FILTER__OPERATOR_TYPE);
            } else {
                return context.deserialize(jsonElement, MailboxFilterCondition.class);
            }
        }
        throw new JsonParseException("Dont know how to deserialize "+type);
    }
}
