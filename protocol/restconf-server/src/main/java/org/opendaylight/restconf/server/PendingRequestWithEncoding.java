/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.util.Date;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A {@link PendingRequest} with an attached {@link MessageEncoding}.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class PendingRequestWithEncoding<T> extends AbstractPendingRequest<T> {
    final MessageEncoding encoding;

    PendingRequestWithEncoding(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final MessageEncoding encoding) {
        super(invariants, session, targetUri);
        this.encoding = requireNonNull(encoding);
    }

    static final HttpHeaders metadataHeaders(final ConfigurationMetadata metadata) {
        return setMetadataHeaders(HEADERS_FACTORY.newEmptyHeaders(), metadata);
    }

    static final HttpHeaders setMetadataHeaders(final HttpHeaders headers, final ConfigurationMetadata metadata) {
        final var etag = metadata.entityTag();
        if (etag != null) {
            headers.set(HttpHeaderNames.ETAG, etag.value());
        }
        final var lastModified = metadata.lastModified();
        if (lastModified != null) {
            // FIXME: uses a thread local: we should be able to do better!
            headers.set(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date.from(lastModified)));
        }
        return headers;
    }
}
