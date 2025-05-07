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
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An {@link AbstractResource} which receives the trailing part of the request part.
 */
@NonNullByDefault
abstract sealed class AbstractLeafResource extends AbstractResource
        permits AbstractEventStreamResource, DataResource, ModulesResource, OperationsResource, YLVResource {
    AbstractLeafResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    final PreparedRequest prepare(final SegmentPeeler peeler, final TransportSession session,
            final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal) {
        return prepare(session, method, targetUri, headers, principal, peeler.remaining());
    }

    abstract PreparedRequest prepare(TransportSession session, ImplementedMethod method, URI targetUri,
        HttpHeaders headers, @Nullable Principal principal, String path);
}
