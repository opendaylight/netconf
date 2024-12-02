/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * RESTCONF /yang-library-version resource, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040, section 3.3.3</a>.
 */
@NonNullByDefault
final class YLVResource extends AbstractLeafResource {
    YLVResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return !path.isEmpty() ? EmptyResponse.NOT_FOUND : switch (method) {
            case GET -> prepareGet(session, targetUri, headers, principal, true);
            case HEAD -> prepareGet(session, targetUri, headers, principal, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : new PendingYangLibraryVersionGet(invariants, session, targetUri, principal, withContent, encoding);
    }
}
