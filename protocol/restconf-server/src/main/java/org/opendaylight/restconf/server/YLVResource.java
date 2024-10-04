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
    PreparedRequest prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return !path.isEmpty() ? NOT_FOUND : switch (method) {
            case GET -> prepareYangLibraryVersionGet(targetUri, headers, principal, true);
            case HEAD -> prepareYangLibraryVersionGet(targetUri, headers, principal, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareYangLibraryVersionGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : new PendingYangLibraryVersionGet(invariants, targetUri, principal, withContent, encoding);
    }
}
