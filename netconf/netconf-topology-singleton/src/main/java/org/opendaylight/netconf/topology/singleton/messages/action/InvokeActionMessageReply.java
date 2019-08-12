/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.action;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.topology.singleton.messages.ContainerNodeMessage;
import org.opendaylight.yangtools.yang.common.RpcError;

public class InvokeActionMessageReply implements Serializable {

    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings("SE_BAD_FIELD")
    private final Collection<? extends RpcError> rpcErrors;
    private final ContainerNodeMessage containerNodeMessage;

    public InvokeActionMessageReply(final @Nullable ContainerNodeMessage containerNodeMessage,
        final @NonNull Collection<? extends RpcError> rpcErrors) {
        this.containerNodeMessage = containerNodeMessage;
        this.rpcErrors = requireNonNull(rpcErrors);
    }

    public @Nullable ContainerNodeMessage getContainerNodeMessage() {

        return containerNodeMessage;
    }

    public @NonNull Collection<? extends RpcError> getRpcErrors() {

        return rpcErrors;
    }

    private Object writeReplace() {
        return new Proxy(this);
    }

    private static class Proxy implements Externalizable {

        private static final long serialVersionUID = 2L;

        private InvokeActionMessageReply invokeActionMessageReply;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public Proxy() {
            //due to Externalizable
        }

        Proxy(final InvokeActionMessageReply invokeActionMessageReply) {
            this.invokeActionMessageReply = invokeActionMessageReply;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeInt(invokeActionMessageReply.getRpcErrors().size());
            for (final RpcError rpcError : invokeActionMessageReply.getRpcErrors()) {
                out.writeObject(rpcError);
            }
            out.writeObject(invokeActionMessageReply.getContainerNodeMessage());
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            final int size = in.readInt();
            final Collection<RpcError> rpcErrors = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                rpcErrors.add((RpcError) in.readObject());
            }
            final ContainerNodeMessage containerNodeMessage = (ContainerNodeMessage) in.readObject();
            invokeActionMessageReply = new InvokeActionMessageReply(containerNodeMessage, rpcErrors);
        }

        private Object readResolve() {

            return invokeActionMessageReply;
        }
    }

}