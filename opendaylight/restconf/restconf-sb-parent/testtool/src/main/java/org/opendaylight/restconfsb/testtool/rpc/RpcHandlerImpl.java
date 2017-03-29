/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.rpc;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import org.opendaylight.restconfsb.testtool.xml.rpc.Rpc;
import org.opendaylight.restconfsb.testtool.xml.rpc.Rpcs;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * RpcHandlerImpl handles rpcs as it is specified in {@link Rpcs} structure.
 */
public class RpcHandlerImpl implements RpcHandler {

    private final Map<String, RpcImplementation> rpcMap = new HashMap<>();

    public RpcHandlerImpl(final SchemaContext schemaContext, final Rpcs rpcs) {
        for (Rpc rpc : rpcs.getRpcList()) {
            RpcImplementation runtime = new RpcImplementation(rpc, schemaContext);
            rpcMap.put(runtime.getIdentifier(), runtime);
        }
    }

    @Override
    public String invokeRpc(final String identifier, NormalizedNode<?, ?> body) {
        final RpcImplementation rpcImplementation = rpcMap.get(identifier);
        if (rpcImplementation == null) {
            throw new WebApplicationException("Implementation not available");
        }
        final String result = rpcImplementation.invoke(body);
        if (result == null) {
            throw new WebApplicationException("Input not found in rpc-file");
        }
        return result;
    }
}
