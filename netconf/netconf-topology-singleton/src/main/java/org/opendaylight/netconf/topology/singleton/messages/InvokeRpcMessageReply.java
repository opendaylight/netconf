/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serializable;
import java.util.Collection;
import org.opendaylight.yangtools.yang.common.RpcError;

public class InvokeRpcMessageReply implements Serializable {


    private final Collection<RpcError> rpcErrors;
    private final NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessageReply(final NormalizedNodeMessage normalizedNodeMessage,
                                 final Collection<RpcError> rpcErrors) {
        this.normalizedNodeMessage = normalizedNodeMessage;
        this.rpcErrors = rpcErrors;
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }

    public Collection<RpcError> getRpcErrors() {
        return rpcErrors;
    }
}
