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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

public interface RemoteDeviceCommunicator extends AutoCloseable {
    // FIXME: document this node
    ListenableFuture<RpcResult<NetconfMessage>> sendRequest(NetconfMessage message, QName rpc);

    @Override
    void close();
}
