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
import java.io.Serial;
import java.io.Serializable;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class InvokeRpcMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final SchemaPathMessage schemaPathMessage;
    private final NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessage(final SchemaPathMessage schemaPathMessage,
                            final @Nullable NormalizedNodeMessage normalizedNodeMessage) {
        this.schemaPathMessage = schemaPathMessage;
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    SchemaPathMessage getSchemaPathMessage() {
        return schemaPathMessage;
    }

    public Absolute getSchemaPath() {
        return schemaPathMessage.getSchemaPath();
    }

    public @Nullable NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }

    private Object writeReplace() {
        return new Proxy(this);
    }

    @Override
    public String toString() {
        return "InvokeRpcMessage [schemaPathMessage=" + schemaPathMessage + ", normalizedNodeMessage="
                + normalizedNodeMessage + "]";
    }

    private static class Proxy implements Externalizable {
        @Serial
        private static final long serialVersionUID = 2L;

        private InvokeRpcMessage invokeRpcMessage;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public Proxy() {
            //due to Externalizable
        }

        Proxy(final InvokeRpcMessage invokeRpcMessage) {
            this.invokeRpcMessage = invokeRpcMessage;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeObject(invokeRpcMessage.getSchemaPathMessage());
            out.writeObject(invokeRpcMessage.getNormalizedNodeMessage());
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            invokeRpcMessage = new InvokeRpcMessage((SchemaPathMessage) in.readObject(),
                    (NormalizedNodeMessage) in.readObject());
        }

        private Object readResolve() {
            return invokeRpcMessage;
        }
    }
}
