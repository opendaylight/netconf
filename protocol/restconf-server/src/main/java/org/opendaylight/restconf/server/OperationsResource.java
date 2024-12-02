/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * RESTCONF /operations resource, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040, section 3.3.2</a>.
 */
@NonNullByDefault
final class OperationsResource extends AbstractLeafResource {
    private static final HeadersResponse METHOD_NOT_ALLOWED_RPC =
        new HeadersResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_RPC);

    OperationsResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareGet(session, targetUri, headers, principal, path, true);
            case HEAD -> prepareGet(session, targetUri, headers, principal, path, false);
            case OPTIONS -> prepareOptions(session, targetUri, principal, path);
            case POST -> preparePost(session, targetUri, headers, principal, path);
            default -> prepareDefault(session, targetUri, path);
        };
    }

    private PreparedRequest prepareGet(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
            apiPath -> new PendingOperationsGet(invariants, session, targetUri, principal, encoding, apiPath,
                withContent));
    }

    private PreparedRequest prepareOptions(final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final String path) {
        return path.isEmpty() ? AbstractPendingOptions.READ_ONLY : requiredApiPath(path,
            apiPath -> new PendingOperationsOptions(invariants, session, targetUri, principal, apiPath));
    }

    // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
    private PreparedRequest preparePost(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        final var accept = chooseOutputEncoding(headers);
        return accept == null ? NOT_ACCEPTABLE_DATA : switch (chooseInputEncoding(headers)) {
            case NOT_PRESENT -> preparePost(session, targetUri, principal, path, invariants.defaultEncoding(), accept);
            case JSON -> preparePost(session, targetUri, principal, path, MessageEncoding.JSON, accept);
            case XML -> preparePost(session, targetUri, principal, path, MessageEncoding.XML, accept);
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
        };
    }

    private PreparedRequest preparePost(final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final String path, final MessageEncoding content,
            final MessageEncoding accept) {
        return optionalApiPath(path,
            apiPath -> new PendingOperationsPost(invariants, session, targetUri, principal, content, accept, apiPath));
    }

    private static PreparedRequest prepareDefault(final TransportSession session, final URI targetUri,
            final String path) {
        return path.isEmpty() ? METHOD_NOT_ALLOWED_READ_ONLY
            // TODO: This is incomplete. We are always reporting 405 Method Not Allowed, but we can do better.
            //       We should fire off an OPTIONS request for the apiPath and see if it exists: if it does not,
            //       we should report a 404 Not Found instead.
            : requiredApiPath(path, apiPath -> METHOD_NOT_ALLOWED_RPC);
    }
}
