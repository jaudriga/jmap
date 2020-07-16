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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.mua.MuaSession;
import rs.ltt.jmap.mua.cache.Cache;
import rs.ltt.jmap.mua.cache.ObjectsState;

import java.util.concurrent.ExecutionException;

public abstract class MuaService {

    private final MuaSession muaSession;
    protected final JmapClient jmapClient;
    protected final Cache cache;
    protected final String accountId;
    protected final ListeningExecutorService ioExecutorService;

    public MuaService(final MuaSession muaSession) {
        this.muaSession = muaSession;
        this.jmapClient = muaSession.getJmapClient();
        this.cache = muaSession.getCache();
        this.accountId = muaSession.getAccountId();
        this.ioExecutorService = muaSession.getIoExecutorService();
    }

    protected  <T extends MuaService> T getService(Class<T> clazz) {
        return muaSession.getService(clazz);
    }

    protected Long getQueryPageSize() {
        return muaSession.getQueryPageSize();
    }

    protected ListenableFuture<ObjectsState> getObjectsState() {
        return ioExecutorService.submit(cache::getObjectsState);
    }

    protected static Throwable extractException(final Exception exception) {
        if (exception instanceof ExecutionException) {
            final Throwable cause = exception.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return exception;
    }

}
