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

package rs.ltt.jmap.common.util;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.common.Utils;
import rs.ltt.jmap.common.entity.AccountCapability;
import rs.ltt.jmap.common.entity.Capability;
import rs.ltt.jmap.common.method.MethodCall;
import rs.ltt.jmap.common.method.MethodErrorResponse;
import rs.ltt.jmap.common.method.MethodResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public final class Mapper {

    private static Logger LOGGER = LoggerFactory.getLogger(Mapper.class);
    public static final ImmutableBiMap<String, Class<? extends MethodCall>> METHOD_CALLS = Mapper.get(MethodCall.class);
    public static final ImmutableBiMap<String, Class<? extends MethodResponse>> METHOD_RESPONSES = Mapper.get(MethodResponse.class);
    public static final ImmutableBiMap<String, Class<? extends MethodErrorResponse>> METHOD_ERROR_RESPONSES = Mapper.get(MethodErrorResponse.class);
    public static final ImmutableBiMap<String, Class<? extends Capability>> CAPABILITIES = Mapper.get(Capability.class);
    public static final ImmutableBiMap<String, Class<? extends AccountCapability>> ACCOUNT_CAPABILITIES = Mapper.get(AccountCapability.class);

    private Mapper() {

    }

    private static <T> ImmutableBiMap<String, Class<? extends T>> get(Class<T> type) {
        final ImmutableBiMap.Builder<String, Class<? extends T>> builder = new ImmutableBiMap.Builder<>();
        for (final BufferedReader bufferedReader : getSystemResources(type)) {
            if (bufferedReader == null) {
                continue;
            }
            try {
                for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
                    final String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        final String name = parts[0];
                        try {
                            Class<? extends T> clazz = Class.forName(name).asSubclass(type);
                            builder.put(parts[1], clazz);
                        } catch (ClassNotFoundException | ClassCastException e) {
                            LOGGER.warn("Mapping points to a class that doesn't exist {}", name);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to read system resource", e);
            }
        }

        return builder.build();
    }

    private static <T> Iterable<BufferedReader> getSystemResources(final Class<T> type) {
        final List<URL> urls = getSystemResourceUrls(type);
        if (urls.size() == 0) {
            final InputStream is = Mapper.class.getClassLoader().getResourceAsStream(Utils.getFilenameFor(type));
            if (is == null) {
                LOGGER.error("Unable to find resources for type {}", type.getName());
                return Collections.emptyList();
            }
            return Collections.singletonList(new BufferedReader(new InputStreamReader(is)));
        } else {
            return Iterables.transform(urls, new Function<URL, BufferedReader>() {
                @NullableDecl
                @Override
                public BufferedReader apply(final URL url) {
                    try {
                        return new BufferedReader(Resources.asCharSource(url, Charsets.UTF_8).openStream());
                    } catch (IOException e) {
                        LOGGER.warn("Unable to to read mappings for type {} from url {}", type.getName(), url.toString());
                        return null;
                    }
                }
            });
        }
    }

    private static <T> List<URL> getSystemResourceUrls(Class<T> type) {
        try {
            return Collections.list(ClassLoader.getSystemResources(Utils.getFilenameFor(type)));
        } catch (IOException e) {
            LOGGER.warn("Unable to get SystemResources from ClassLoader", e);
            return Collections.emptyList();
        }
    }

}
