/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to
 * {@link ContainerNode}.
 */
public final class NetconfDeviceRpc implements Rpcs.Normalized {
    private final @NonNull NetconfDeviceDOMRpcService domRpcService;

    public NetconfDeviceRpc(final EffectiveModelContext modelContext, final RemoteDeviceCommunicator communicator,
            final RpcTransformer<ContainerNode, DOMRpcResult> transformer) {
        domRpcService = new NetconfDeviceDOMRpcService(modelContext, communicator, transformer);
    }

    @Override
    public DOMRpcService domRpcService() {
        return domRpcService;
    }
}
