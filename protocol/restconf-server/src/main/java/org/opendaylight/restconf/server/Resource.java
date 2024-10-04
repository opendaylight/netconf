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
import java.sql.PreparedStatement;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * An HTTP resource which can service requests.
 */
@NonNullByDefault
abstract sealed class Resource permits AbstractResource, IntermediateResource {
    static final CompletedRequest NOT_FOUND = new DefaultCompletedRequest(HttpResponseStatus.NOT_FOUND);

    /**
     * Prepare to service a request, by binding the request HTTP method and the request path to a resource and
     * validating request headers in that context. This method is required to not block.
     *
     * @param peeler the {@link SegmentPeeler} holding the unprocessed part of the request path
     * @param session the {@link TransportSession} on which this request is being invoked
     * @param method the method being invoked
     * @param targetUri the URI of the target resource
     * @param headers request headers
     * @param principal the {@link Principal} making this request, {@code null} if not known
     * @return A {@link PreparedStatement}
     */
    abstract PreparedRequest prepare(SegmentPeeler peeler, TransportSession session, ImplementedMethod method,
        URI targetUri, HttpHeaders headers, @Nullable Principal principal);
}
