/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.rpc;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.LinkedList;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcError;

public class InvokeRpcMessageReply implements Externalizable {
    private static final long serialVersionUID = 1L;

    private Collection<RpcError> rpcErrors;
    private NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessageReply() {
        // due to Externalizable interface
    }

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

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(rpcErrors.size());
        for (final RpcError rpcError : rpcErrors) {
            out.writeObject(rpcError);
        }
        out.writeObject(normalizedNodeMessage);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        final int size = in.readInt();
        rpcErrors = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            rpcErrors.add((RpcError) in.readObject());
        }
        normalizedNodeMessage = (NormalizedNodeMessage) in.readObject();
    }
}
