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
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class InvokeRpcMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final SchemaPathMessage schemaPathMessage;
    private final NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessage(final SchemaPathMessage schemaPathMessage,
                            final NormalizedNodeMessage normalizedNodeMessage) {
        this.schemaPathMessage = schemaPathMessage;
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    private SchemaPathMessage getSchemaPathMessage() {
        return schemaPathMessage;
    }

    public SchemaPath getSchemaPath() {
        return schemaPathMessage.getSchemaPath();
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }

    private Object writeReplace() {
        return new Proxy(this);
    }

    private static class Proxy implements Externalizable {
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
