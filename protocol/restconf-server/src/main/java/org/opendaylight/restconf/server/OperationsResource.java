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

/**
 * RESTCONF /operations resource, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040, section 3.3.2</a>.
 */
@NonNullByDefault
final class OperationsResource extends AbstractResource {
    private static final CompletedRequest METHOD_NOT_ALLOWED_RPC =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_RPC);

    OperationsResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareOperationsGet(targetUri, headers, principal, path, true);
            case HEAD -> prepareOperationsGet(targetUri, headers, principal, path, false);
            case OPTIONS -> prepareOperationsOptions(targetUri, principal, path);
            case POST -> prepareOperationsPost(targetUri, headers, principal, path);
            default -> prepareOperationsDefault(targetUri, path);
        };
    }

    private PreparedRequest prepareOperationsGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
            apiPath -> new PendingOperationsGet(invariants, targetUri, principal, encoding, apiPath, withContent));
    }

    private PreparedRequest prepareOperationsOptions(final URI targetUri, final @Nullable Principal principal,
            final String path) {
        return path.isEmpty() ? AbstractPendingOptions.READ_ONLY
            : requiredApiPath(path, apiPath -> new PendingOperationsOptions(invariants, targetUri, principal, apiPath));
    }

    // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
    private PreparedRequest prepareOperationsPost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        final var accept = chooseOutputEncoding(headers);
        return accept == null ? NOT_ACCEPTABLE_DATA : switch (chooseInputEncoding(headers)) {
            case NOT_PRESENT ->
                prepareOperationsPost(targetUri, principal, path, invariants.defaultEncoding(), accept);
            case JSON -> prepareOperationsPost(targetUri, principal, path, MessageEncoding.JSON, accept);
            case XML -> prepareOperationsPost(targetUri, principal, path, MessageEncoding.XML, accept);
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
        };
    }

    private PreparedRequest prepareOperationsPost(final URI targetUri, final @Nullable Principal principal,
            final String path, final MessageEncoding content, final MessageEncoding accept) {
        return optionalApiPath(path,
            apiPath -> new PendingOperationsPost(invariants, targetUri, principal, content, accept, apiPath));
    }

    private static PreparedRequest prepareOperationsDefault(final URI targetUri, final String path) {
        return path.isEmpty() ? METHOD_NOT_ALLOWED_READ_ONLY
            // TODO: This is incomplete. We are always reporting 405 Method Not Allowed, but we can do better.
            //       We should fire off an OPTIONS request for the apiPath and see if it exists: if it does not,
            //       we should report a 404 Not Found instead.
            : requiredApiPath(path, apiPath -> METHOD_NOT_ALLOWED_RPC);
    }

}
