/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.io.InputStream;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ConsumableBody;

/**
 * A {@link PendingRequestWithEncoding} with a significant {@link ConsumableBody}. This class communicates takes care
 * of wrapping the incoming {@link InputStream} body with the corresponding {@link ConsumableBody} and ensures it gets
 * deallocated when no longer needed.
 *
 * @param <T> server response type
 * @param <B> request message body type
 */
@NonNullByDefault
abstract class PendingRequestWithBody<T, B extends ConsumableBody> extends PendingRequestWithEncoding<T> {
    PendingRequestWithBody(final EndpointInvariants invariants, final URI targetUri, final MessageEncoding encoding) {
        super(invariants, targetUri, encoding);
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
}
