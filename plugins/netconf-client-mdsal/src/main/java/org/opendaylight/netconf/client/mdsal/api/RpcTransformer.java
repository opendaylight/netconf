/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

/**
 * Interface for transforming NETCONF device RPC request/response messages.
 *
 * @param <P> {@code rpc} input payload type
 * @param <R> {@code rpc} result type
 */
public interface RpcTransformer<P, R> {

    NetconfMessage toRpcRequest(QName rpc, P payload);

    R toRpcResult(RpcResult<NetconfMessage> resultPayload, QName rpc);
}
