/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import com.google.common.annotations.Beta;
import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;

/**
 * A {@link HTTPServer} resource, which is provided with linkage of other resources existing at the server. Methods
 * defined in this interface may be invoked from multiple threads concurrently.
 */
// TODO: that concurrent requirement seems wasteful, we can do better if we add another indirection, namely
//
//           WebHostSession WebHostResource.bindSession(HTTPTransportChannel channel);
//
//       Then we could be allocating something like a TransportSession when a HTTPServerSession first touches a
//       WebHostResource via Map.computeIfAbsent(key -> WebHostSession).prepare(...)
@Beta
@NonNullByDefault
public interface WebHostResource {
    /**
     * Prepare an HTTP request for execution.
     *
     * @param method the method being invoked
     * @param targetUri the target resource
     * @param headers request headers
     * @param peeler the {@link SegmentPeeler} initialized to point from this resource to the target resource
     * @param xrd the {@link XRD} of this web host
     * @return a {@link PreparedRequest}
     * @throws IllegalStateException if this resource has been destroyed
     */
    PreparedRequest prepare(ImplementedMethod method, URI targetUri, HttpHeaders headers, SegmentPeeler peeler,
        XRD xrd);
}
