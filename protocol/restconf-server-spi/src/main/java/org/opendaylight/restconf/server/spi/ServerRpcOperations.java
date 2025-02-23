/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.annotations.Beta;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A concrete implementation of RESTCONF server, as specified by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>, based on
 * {@code yang-data-api}.
 */
@NonNullByDefault
public interface ServerRpcOperations {
    /**
     * Invoke an {@code rpc}.
     *
     * @param request {@link ServerRequest} for this request
     * @param restconfURI Base URI of the request, the absolute equivalent to {@code {+restconf}} URI with a trailing
     *                    slash
     * @param path data path for this request
     * @param input action {@code input}
     */
    @Beta
    void invokeRpc(ServerRequest<InvokeResult> request, URI restconfURI, Rpc path, ContainerNode input);
}
