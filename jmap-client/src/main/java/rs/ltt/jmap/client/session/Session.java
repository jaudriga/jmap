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

package rs.ltt.jmap.client.session;

import com.google.common.base.Preconditions;
import okhttp3.HttpUrl;
import rs.ltt.jmap.common.SessionResource;

public class Session {

    private final HttpUrl base;

    private final SessionResource sessionResource;

    public Session(HttpUrl base, SessionResource sessionResource) {
        this.base = base;
        this.sessionResource = sessionResource;
    }

    public HttpUrl getApiUrl() {
        final String apiUrl = sessionResource.getApiUrl();
        final HttpUrl.Builder builder = base.newBuilder(apiUrl);
        Preconditions.checkState(builder != null, String.format("Unable to assemble final API Url from base=%s and apiUrl=%s", base, apiUrl));
        return builder.build();
    }

    public HttpUrl getBase() {
        return base;
    }

    public String getState() {
        return sessionResource.getState();
    }
}
