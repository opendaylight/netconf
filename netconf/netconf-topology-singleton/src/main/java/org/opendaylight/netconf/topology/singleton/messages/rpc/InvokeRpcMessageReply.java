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
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcError;

public class InvokeRpcMessageReply implements Serializable {
    private static final long serialVersionUID = 1L;

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

    private Object writeReplace() {
        return new Proxy(this);
    }

    private static class Proxy implements Externalizable {
        private static final long serialVersionUID = 2L;

        private InvokeRpcMessageReply invokeRpcMessageReply;

        Proxy() {
            //due to Externalizable
        }

        Proxy(final InvokeRpcMessageReply invokeRpcMessageReply) {
            this.invokeRpcMessageReply = invokeRpcMessageReply;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(invokeRpcMessageReply.getRpcErrors().size());
            for (final RpcError rpcError : invokeRpcMessageReply.getRpcErrors()) {
                out.writeObject(rpcError);
            }
            out.writeObject(invokeRpcMessageReply.getNormalizedNodeMessage());
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            final int size = in.readInt();
            final Collection<RpcError> rpcErrors = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                rpcErrors.add((RpcError) in.readObject());
            }
            final NormalizedNodeMessage normalizedNodeMessage = (NormalizedNodeMessage) in.readObject();
            invokeRpcMessageReply = new InvokeRpcMessageReply(normalizedNodeMessage, rpcErrors);
        }

        private Object readResolve() {
            return invokeRpcMessageReply;
        }
    }

}
