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

package rs.ltt.jmap.mua;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.Credentials;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import rs.ltt.jmap.common.GenericResponse;
import rs.ltt.jmap.common.Request;
import rs.ltt.jmap.common.SessionResource;
import rs.ltt.jmap.gson.JmapAdapters;

public abstract class JmapDispatcher extends Dispatcher {

    public static final String ACCOUNT_ID = "test@example.com";
    public static final String USERNAME = "test@example.com";
    public static final String PASSWORD = "secret";
    public static String WELL_KNOWN_PATH = ".well-known/jmap";

    private static final Gson GSON;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        JmapAdapters.register(gsonBuilder);
        GSON = gsonBuilder.create();
    }

    private int sessionState = 0;

    @Override
    public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
        switch (request.getPath()) {
            case "/.well-known/jmap":
                if ("GET".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(301).addHeader("Location: /jmap/");
                } else {
                    return new MockResponse().setResponseCode(404);
                }
            case "/jmap/":
                final String authorization = request.getHeader("Authorization");
                if (!Credentials.basic(USERNAME, PASSWORD).equals(authorization)) {
                    return new MockResponse().setResponseCode(401);
                }
                if ("GET".equals(request.getMethod())) {
                    return session(request);
                } else if ("POST".equals(request.getMethod())) {
                    return request(request);
                } else {
                    return new MockResponse().setResponseCode(404);
                }
            default:
                return new MockResponse().setResponseCode(404);
        }
    }

    protected void incrementSessionState() {
        this.sessionState++;
    }

    protected String getSessionState() {
        return String.valueOf(this.sessionState);
    }

    private MockResponse session(final RecordedRequest request) {
        final SessionResource sessionResource = SessionResource.builder()
                .apiUrl("/jmap/")
                .state(getSessionState())
                .build();

        return new MockResponse().setBody(GSON.toJson(sessionResource));
    }

    private MockResponse request(final RecordedRequest recordedRequest) {
        //TODO check application type
        //TODO check for parse errors
        final GenericResponse response = dispatch(GSON.fromJson(recordedRequest.getBody().readUtf8(), Request.class));
        return new MockResponse().setResponseCode(200).setBody(GSON.toJson(response));
    }

    protected abstract GenericResponse dispatch(Request request);
}
