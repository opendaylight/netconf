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
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A {@link PendingRequestWithEncoding} with a significant {@link ConsumableBody}. This class communicates takes care
 * of wrapping the incoming {@link InputStream} body with the corresponding {@link ConsumableBody} and ensures it gets
 * deallocated when no longer needed.
 *
 * @param <T> server response type
 * @param <B> request message body type
 */
@NonNullByDefault
abstract class PendingRequestWithBody<T, B extends ConsumableBody> extends AbstractPendingRequest<T> {
    // Note naming: derived from 'Content-Type'
    final MessageEncoding contentEncoding;

    PendingRequestWithBody(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding) {
        super(invariants, session, targetUri, principal);
        this.contentEncoding = requireNonNull(contentEncoding);
    }

    @Override
    final void execute(final NettyServerRequest<T> request, final InputStream body) {
        try (var wrapped = wrapBody(body)) {
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

    final Response transformCreateResource(final NettyServerRequest<?> request, final CreateResourceResult result) {
        return new DefaultCompletedRequest(HttpResponseStatus.CREATED,
            metadataHeaders(result).set(HttpHeaderNames.LOCATION, restconfURI() + "data/" + result.createdPath()));
    }

    static final Response transformInvoke(final NettyServerRequest<?> request, final InvokeResult result,
            final MessageEncoding acceptEncoding) {
        final var output = result.output();
        return output == null ? NO_CONTENT : new FormattableDataResponse(output, acceptEncoding, request.prettyPrint());
    }
}
