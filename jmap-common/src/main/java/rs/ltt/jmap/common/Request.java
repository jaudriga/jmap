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

package rs.ltt.jmap.common;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import rs.ltt.jmap.common.method.MethodCall;
import rs.ltt.jmap.common.method.call.submission.SetEmailSubmissionMethodCall;
import rs.ltt.jmap.common.util.Namespace;

import java.util.*;

public class Request {

    private static final Map<Class<? extends MethodCall>, List<String>> NAMESPACE_CACHE = new HashMap<>();
    private final String[] using;
    private final Invocation[] methodCalls;

    private Request(String[] using, Invocation[] methodCalls) {
        this.using = using;
        this.methodCalls = methodCalls;
    }

    private static List<String> getNamespacesFor(Class<? extends MethodCall> clazz) {
        synchronized (NAMESPACE_CACHE) {
            final List<String> cached = NAMESPACE_CACHE.get(clazz);
            if (cached != null) {
                return cached;
            }
            final String namespace = Namespace.get(clazz);
            if (namespace == null) {
                throw new IllegalArgumentException(
                        String.format("%s is missing a namespace. Annotate package with @JmapNamespace", clazz.getSimpleName())
                );
            }
            ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<>();
            listBuilder.add(namespace);
            listBuilder.addAll(Namespace.getImplicit(clazz));
            List<String> namespaces = listBuilder.build();
            NAMESPACE_CACHE.put(clazz, namespaces);
            return namespaces;
        }
    }

    public String[] getUsing() {
        return using;
    }

    public Invocation[] getMethodCalls() {
        return methodCalls;
    }

    public static class Invocation {

        private MethodCall methodCall;
        private String id;

        private Invocation() {

        }

        public Invocation(MethodCall methodCall, String id) {
            this.methodCall = methodCall;
            this.id = id;
        }

        public MethodCall getMethodCall() {
            return methodCall;
        }

        public ResultReference createReference(String path) {
            return new ResultReference(id, methodCall.getClass(), path);
        }

        public String getId() {
            return id;
        }

        public static class ResultReference {

            private final String id;
            private final Class<? extends MethodCall> clazz;
            private final String path;

            private ResultReference(String id, Class<? extends MethodCall> clazz, String path) {
                this.id = id;
                this.clazz = clazz;
                this.path = path;
            }

            public String getId() {
                return id;
            }

            public String getPath() {
                return path;
            }

            public Class<? extends MethodCall> getClazz() {
                return clazz;
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                        .add("id", id)
                        .add("clazz", clazz)
                        .add("path", path)
                        .toString();
            }

            public static final class Path {
                public static final String IDS = "/ids";
                public static final String ADDED_IDS = "/added/*/id";
                public static final String LIST_IDS = "/list/*/id";
                public static final String LIST_THREAD_IDS = "/list/*/threadId";
                public static final String LIST_EMAIL_IDS = "/list/*/emailIds";
                public static final String UPDATED = "/updated";
                public static final String CREATED = "/created";
                public static final String UPDATED_PROPERTIES = "/updatedProperties";
            }
        }
    }

    public static class Builder {

        private List<Invocation> invocations = new ArrayList<>();
        private Set<String> using = new TreeSet<>();

        public Builder() {

        }

        public Builder call(MethodCall call) {
            final int id = invocations.size();
            final Invocation invocation = new Invocation(call, String.valueOf(id));
            return add(invocation);
        }

        public Builder add(Invocation invocation) {
            this.invocations.add(invocation);
            final Class<? extends MethodCall> clazz = invocation.methodCall.getClass();
            this.using.addAll(getNamespacesFor(clazz));
            if (invocation.methodCall instanceof SetEmailSubmissionMethodCall) {
                final SetEmailSubmissionMethodCall call = (SetEmailSubmissionMethodCall) invocation.methodCall;
                if (call.getOnSuccessUpdateEmail() != null && !call.getOnSuccessUpdateEmail().isEmpty()) {
                    this.using.add(rs.ltt.jmap.Namespace.MAIL);
                }
            }
            return this;
        }

        public Request build() {
            return new Request(using.toArray(new String[0]), invocations.toArray(new Invocation[0]));
        }
    }
}
