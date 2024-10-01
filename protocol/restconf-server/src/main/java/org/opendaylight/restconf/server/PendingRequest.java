/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * A {@link PreparedRequest} which is pending execution. The request is known to be bound to invoke some HTTP method on
 * some resource.
 *
 * @apiNote This is an abstract class so that we use identity for equality and enforce a few other design details.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract non-sealed class PendingRequest<T> implements PreparedRequest {
    final EndpointInvariants invariants;

    PendingRequest(final EndpointInvariants invariants) {
        this.invariants = requireNonNull(invariants);
    }

    final RestconfServer server() {
        return invariants.server();
    }

    /**
     * Return the absolute URI pointing at the root API resource, as seen from the perspective of specified request.
     *
     * @param request a {@link NettyServerRequest}
     * @return An absolute URI
     */
    final URI restconfURI(final NettyServerRequest<?> request) {
        return request.requestUri().resolve(invariants.restconfPath());
    }

    final void execute(final RequestCompleter completer, final @Nullable Principal principal, final URI targetUri,
            final InputStream body) {
        execute(new NettyServerRequest<>(this, completer, principal, targetUri), body);
    }

    abstract void execute(NettyServerRequest<T> request, InputStream body);

    final void onFailure(final RequestCompleter completer, final NettyServerRequest<T> request,
            final HttpStatusCode status, final FormattableBody body) {
        // FIXME: need to pick encoding
        completer.completeRequest(this, null);
        // FIXME:
//        callback.onSuccess(responseBuilder(requestParams, HttpResponseStatus.valueOf(status.code()))
//            .setBody(body)
//            .build());
    }

    final void onSuccess(final RequestCompleter completer, final NettyServerRequest<T> request, final T result) {
        // FIXME: handle transform failures
        completer.completeRequest(this, transformResult(request, result));
    }

    abstract Response transformResult(NettyServerRequest<?> request, T result);

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(final @Nullable Object obj) {
        return super.equals(obj);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
