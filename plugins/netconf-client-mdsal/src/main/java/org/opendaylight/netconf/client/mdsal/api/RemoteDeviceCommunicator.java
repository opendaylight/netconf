/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.yangtools.yang.common.RpcResult;

public interface RemoteDeviceCommunicator extends AutoCloseable {
    /**
     * Send request message to current client session.
     *
     * @param message {@link NetconfMessage} to be sent
     * @return A {@link ListenableFuture} which completes with result of sending give message
     *         represented by {@link RpcResult}
     */
    ListenableFuture<RpcResult<NetconfMessage>> sendRequest(NetconfMessage message);

    @Override
    void close();
}
