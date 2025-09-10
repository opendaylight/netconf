/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An {@link AbstractPendingRequest} with a significant {@link ConsumableBody}. This class communicates takes care
 * of wrapping the incoming {@link InputStream} body with the corresponding {@link ConsumableBody} and ensures it gets
 * deallocated when no longer needed.
 *
 * @param <T> server response type
 * @param <B> request message body type
 */
@NonNullByDefault
abstract non-sealed class PendingRequestWithBody<T, B extends ConsumableBody> extends AbstractPendingRequest<T> {
    // Note naming: derived from 'Content-Type'
    final MessageEncoding contentEncoding;

    PendingRequestWithBody(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding) {
        super(invariants, session, targetUri, principal);
        this.contentEncoding = requireNonNull(contentEncoding);
    }

    @Override
    MessageEncoding errorEncoding() {
        // TODO: this is not quite right: we should be looking at the Accept header
        return contentEncoding;
    }

    @Override
    MessageEncoding requestEncoding() {
        return contentEncoding;
    }

    @Override
    final void execute(final NettyServerRequest<T> request, final @Nullable InputStream body) {
        // Our APIs require the body to be present due to how JAX-RS operates. If we have gotten rid of the body, or
        // the user has not supplied one, provide an empty body here.
        //
        // TODO: Once we do not need to worry about JAX-RS, let's revisit RestconfServer's APIs and allow passing a null
        //       to indicate an empty/missing body. That may be problematic, but at least OperationInputBody would
        //       benefit.
        try (var wrapped = wrapBody(body != null ? body : InputStream.nullInputStream())) {
            execute(request, wrapped);
        }
    }

    abstract void execute(NettyServerRequest<T> request, B body);

    /**
     * Returns the provided {@link InputStream} body wrapped with request-specific {@link ConsumableBody}.
     *
     * @param body body as an {@link InputStream}
     * @return body as a {@link ConsumableBody}
     */
    abstract B wrapBody(InputStream body);

    final HeadersResponse transformCreateResource(final CreateResourceResult result) {
        final var headers = metadataHeaders(result);
        headers.add(HttpHeaderNames.LOCATION);
        headers.add(restconfURI() + "data/" + result.createdPath());
        return HeadersResponse.of(HttpResponseStatus.CREATED, headers);
    }

    static final Response transformInvoke(final NettyServerRequest<?> request, final InvokeResult result,
            final MessageEncoding acceptEncoding) {
        final var output = result.output();
        return output == null ? EmptyResponse.NO_CONTENT
            : new FormattableDataResponse(output, acceptEncoding, request.prettyPrint());
    }
}
