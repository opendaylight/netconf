/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AbstractPendingRequest} without a body. This class takes care of releasing any {@link InputStream} passed
 * in as body.
 */
@NonNullByDefault
abstract non-sealed class PendingRequestWithoutBody<T> extends AbstractPendingRequest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(PendingRequestWithoutBody.class);

    PendingRequestWithoutBody(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal) {
        super(invariants, session, targetUri, principal);
    }

    @Override
    MessageEncoding errorEncoding() {
        return invariants.defaultEncoding();
    }

    @Override
    MessageEncoding requestEncoding() {
        return invariants.defaultEncoding();
    }

    @Override
    final void execute(final NettyServerRequest<T> request, final @Nullable InputStream body) {
        if (body != null) {
            closeBody(body);
        }
        execute(request);
    }

    abstract void execute(NettyServerRequest<T> request);

    private void closeBody(final InputStream body) {
        LOG.debug("Unexpected body in {}, closing it", this);
        try {
            body.close();
        } catch (IOException e) {
            // Not much else we can do, really
            LOG.warn("Failed to close unexpected body, proceeding anyway", e);
        }
    }
}
