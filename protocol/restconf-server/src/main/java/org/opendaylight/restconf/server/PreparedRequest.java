/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

/**
 * The result of {@link RestconfServerResource#prepareRequest(SegmentPeeler, org.opendaylight.restconf.server.api.TransportSession,
 * ImplementedMethod, java.net.URI, io.netty.handler.codec.http.HttpHeaders, java.security.Principal)}.
 * This can either be a {@link CompletedRequest} or a {@link PendingRequest}.
 */
sealed interface PreparedRequest permits CompletedRequest, PendingRequest {
    // Nothing else
}
