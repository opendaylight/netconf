/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A RESTCONF server implementation, independent of the actual bound {@link URI}.
 */
@NonNullByDefault
public interface RestconfServer {
    /**
     * Inbound request information.
     *
     * @param serverRoot the root URI of the HTTP-facing server through which this request is executing
     * @param principal the entity making the request
     */
    public record RequestInfo(URI serverRoot, Principal principal) {
        public RequestInfo {
            requireNonNull(serverRoot);
            requireNonNull(principal);
        }
    }

    /**
     * Invoke an RPC identified by an {@link ApiPath}, in the context of a requesting {@link Principal}.
     *
     * @param principal invocation principal
     * @param rpc request path
     * @param input rpc input
     * @return A {@link RestconfServerFuture} completing with the invocation result.
     */
    RestconfServerFuture<ContainerNode> invokeRpc(RequestInfo info, ApiPath rpc, ContainerNode input);
}
