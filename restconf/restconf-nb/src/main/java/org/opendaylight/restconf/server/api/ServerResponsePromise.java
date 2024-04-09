/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.opendaylight.restconf.server.api.ServerResponse.Success;

/**
 * A producer-side of {@link ServerResponseFuture}.
 *
 * @param <T> success type
 */
public final class ServerResponsePromise<T extends Success> implements ServerResponseFuture<T> {
    private final CompletableFuture<ServerResponse> future = new CompletableFuture<>();

    public boolean completeWith(final ServerResponse response) {
        return future.complete(requireNonNull(response));
    }

    @Override
    public ServerResponseFuture<T> onComplete(final OnComplete<? super T> onComplete, final Executor executor) {
        future.whenComplete((response, ignored) -> ImmediateServerResponse.onComplete(onComplete, response));
        return this;
    }
}
