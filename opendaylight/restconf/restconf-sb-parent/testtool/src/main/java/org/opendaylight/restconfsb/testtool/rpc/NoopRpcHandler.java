/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.rpc;

import javax.ws.rs.WebApplicationException;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * NoopRpcHandler returns 404 Not found status for all rpcs.
 */
public class NoopRpcHandler implements RpcHandler {

    @Override
    public String invokeRpc(String identifier, NormalizedNode<?, ?> body) {
        throw new WebApplicationException(404);
    }
}
