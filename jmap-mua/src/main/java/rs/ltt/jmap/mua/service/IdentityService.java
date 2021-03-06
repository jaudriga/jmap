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

package rs.ltt.jmap.mua.service;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.common.entity.Identity;
import rs.ltt.jmap.common.method.call.identity.GetIdentityMethodCall;
import rs.ltt.jmap.common.method.response.identity.ChangesIdentityMethodResponse;
import rs.ltt.jmap.common.method.response.identity.GetIdentityMethodResponse;
import rs.ltt.jmap.mua.MuaSession;
import rs.ltt.jmap.mua.Status;
import rs.ltt.jmap.mua.cache.Update;
import rs.ltt.jmap.mua.util.UpdateUtil;

public class IdentityService extends MuaService {

    public IdentityService(final MuaSession muaSession) {
        super(muaSession);
    }

    public ListenableFuture<Status> refreshIdentities() {
        final ListenableFuture<String> identityStateFuture = ioExecutorService.submit(cache::getIdentityState);
        return Futures.transformAsync(identityStateFuture, state -> {
            if (state == null) {
                return loadIdentities();
            } else {
                return updateIdentities(state);
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Status> loadIdentities() {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> future = loadIdentities(multiCall);
        multiCall.execute();
        return future;
    }

    private ListenableFuture<Status> loadIdentities(final JmapClient.MultiCall multiCall) {
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final ListenableFuture<MethodResponses> responseFuture = multiCall.call(
                GetIdentityMethodCall.builder().accountId(accountId).build()
        ).getMethodResponses();
        responseFuture.addListener(() -> {
            try {
                final GetIdentityMethodResponse response = responseFuture.get().getMain(GetIdentityMethodResponse.class);
                final Identity[] identities = response.getList();
                cache.setIdentities(response.getTypedState(), identities);
                settableFuture.set(Status.of(identities.length > 0));
            } catch (Exception e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);
        return settableFuture;
    }

    private ListenableFuture<Status> updateIdentities(final String state) {
        final JmapClient.MultiCall multiCall = jmapClient.newMultiCall();
        final ListenableFuture<Status> future = updateIdentities(state, multiCall);
        multiCall.execute();
        return future;
    }

    private ListenableFuture<Status> updateIdentities(final String state, final JmapClient.MultiCall multiCall) {
        Preconditions.checkNotNull(state, "State can not be null when updating identities");
        final SettableFuture<Status> settableFuture = SettableFuture.create();
        final UpdateUtil.MethodResponsesFuture methodResponsesFuture = UpdateUtil.identities(multiCall, accountId, state);
        methodResponsesFuture.addListener(() -> {
            try {
                ChangesIdentityMethodResponse changesResponse = methodResponsesFuture.changes(ChangesIdentityMethodResponse.class);
                GetIdentityMethodResponse createdResponse = methodResponsesFuture.created(GetIdentityMethodResponse.class);
                GetIdentityMethodResponse updatedResponse = methodResponsesFuture.updated(GetIdentityMethodResponse.class);
                final Update<Identity> update = Update.of(changesResponse, createdResponse, updatedResponse);
                if (update.hasChanges()) {
                    cache.updateIdentities(update);
                }
                settableFuture.set(Status.of(update));
            } catch (Exception e) {
                settableFuture.setException(extractException(e));
            }
        }, ioExecutorService);
        return settableFuture;
    }

}
