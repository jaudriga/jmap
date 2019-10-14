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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.client.api.EndpointNotFoundException;
import rs.ltt.jmap.client.api.UnauthorizedException;
import rs.ltt.jmap.client.http.HttpAuthentication;
import rs.ltt.jmap.client.util.WellKnownUtil;
import rs.ltt.jmap.common.SessionResource;
import rs.ltt.jmap.gson.JmapAdapters;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class SessionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionClient.class);

    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();

    private final URL sessionResource;
    private HttpAuthentication httpAuthentication;
    private SessionCache sessionCache;
    private Session currentSession = null;

    public SessionClient(HttpAuthentication authentication) {
        this.sessionResource = null;
        this.httpAuthentication = authentication;
    }

    public SessionClient(HttpAuthentication authentication, URL sessionResource) {
        this.sessionResource = sessionResource;
        this.httpAuthentication = authentication;
    }

    public Session get() throws Exception {
        final Session existingSession = this.currentSession;
        if (existingSession != null) {
            return existingSession;
        }
        synchronized (this) {
            if (currentSession != null) {
                return currentSession;
            }
            final String username = httpAuthentication.getUsername();
            final URL resource;
            if (sessionResource != null) {
                resource = sessionResource;
            } else {
                resource = WellKnownUtil.fromUsername(username);
            }

            final SessionCache cache = sessionCache;
            Session session = cache != null ? cache.load(username, resource) : null;

            if (session == null) {
                session = fetchSession(resource);
                if (cache != null) {
                    cache.store(username, resource, session);
                }
            }

            currentSession = session;

        }
        return currentSession;
    }

    private Session fetchSession(final URL base) throws Exception {
        final Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(base);
        httpAuthentication.authenticate(requestBuilder);

        final Response response = OK_HTTP_CLIENT.newCall(requestBuilder.build()).execute();
        final int code = response.code();
        if (code == 200 || code == 201) {
            final ResponseBody body = response.body();
            if (body == null) {
                throw new EndpointNotFoundException("Unable to fetch session object. Response body was empty.");
            }
            final InputStream inputStream = body.byteStream();
            final GsonBuilder builder = new GsonBuilder();
            JmapAdapters.register(builder);
            final Gson gson = builder.create();
            final SessionResource sessionResource = gson.fromJson(new InputStreamReader(inputStream), SessionResource.class);
            final HttpUrl currentBaseUrl = response.request().url();
            if (!base.equals(currentBaseUrl.url())) {
                LOGGER.info("Processed new base URL {}", currentBaseUrl.url());
            }
            return new Session(currentBaseUrl.url(), sessionResource);
        } else if (code == 401) {
            throw new UnauthorizedException(String.format("Session object(%s) was unauthorized", base.toString()));
        } else {
            throw new EndpointNotFoundException(String.format("Unable to fetch session object(%s)", base.toString()));
        }
    }

    public void setSessionCache(SessionCache sessionCache) {
        this.sessionCache = sessionCache;
    }

}
