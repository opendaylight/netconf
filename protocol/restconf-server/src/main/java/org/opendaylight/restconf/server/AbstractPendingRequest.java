/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * An abstract implementation of {@link PendingRequest} contract for RESTCONF endpoint.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class AbstractPendingRequest<T> extends PendingRequest<T> {
    static final CompletedRequest NO_CONTENT = new DefaultCompletedRequest(HttpResponseStatus.NO_CONTENT);
    static final DefaultHttpHeadersFactory HEADERS_FACTORY =
        DefaultHttpHeadersFactory.headersFactory().withCombiningHeaders(true);

    final EndpointInvariants invariants;
    final URI targetUri;

    AbstractPendingRequest(final EndpointInvariants invariants, final URI targetUri) {
        this.invariants = requireNonNull(invariants);
        this.targetUri = requireNonNull(targetUri);
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
    final URI restconfURI() {
        return targetUri.resolve(invariants.restconfPath());
    }

    @Override
    final void execute(final RequestCompleter completer, final @Nullable Principal principal, final InputStream body) {
        execute(new NettyServerRequest<>(this, completer, principal), body);
    }

    abstract void execute(NettyServerRequest<T> request, InputStream body);

    final void onFailure(final RequestCompleter completer, final NettyServerRequest<T> request,
            final HttpStatusCode status, final FormattableBody body) {
        completer.requestComplete(this, new FormattableDataResponse(HttpResponseStatus.valueOf(status.code()), null,
            // FIXME: need to pick encoding
            body, null, request.prettyPrint()));
    }

    final void onSuccess(final RequestCompleter completer, final NettyServerRequest<T> request, final T result) {
        // FIXME: handle transform failures
        completer.requestComplete(this, transformResult(request, result));
    }

    /**
     * Transform a RestconfServer result to a {@link Response}.
     *
     * @param request {@link NettyServerRequest} handle
     * @param result the result
     * @return A {@link Response}
     */
    abstract Response transformResult(NettyServerRequest<?> request, T result);
}
