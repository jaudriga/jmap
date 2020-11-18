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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import rs.ltt.jmap.common.GenericResponse;
import rs.ltt.jmap.common.Request;
import rs.ltt.jmap.common.Response;
import rs.ltt.jmap.common.method.MethodCall;
import rs.ltt.jmap.common.method.MethodResponse;

public abstract class SimpleJmapDispatcher extends JmapDispatcher {

    @Override
    protected GenericResponse dispatch(final Request request) {
        final ArrayListMultimap<String, Response.Invocation> response = ArrayListMultimap.create();
        for(final Request.Invocation invocation : request.getMethodCalls()) {
            final String id = invocation.getId();
            final MethodCall methodCall = invocation.getMethodCall();
            for(MethodResponse methodResponse : dispatch(methodCall, ImmutableListMultimap.copyOf(response))) {
                response.put(id, new Response.Invocation(methodResponse, id));
            }
        }
        return new Response(
                response.values().toArray(new Response.Invocation[0]),
                getSessionState()
        );
    }

    protected abstract MethodResponse[] dispatch(
            final MethodCall methodCall,
            final ListMultimap<String, Response.Invocation> previousResponses
    );

}
