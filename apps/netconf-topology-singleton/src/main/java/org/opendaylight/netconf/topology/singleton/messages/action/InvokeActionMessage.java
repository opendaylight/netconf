/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.action;

import static java.util.Objects.requireNonNull;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.io.Serializable;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.topology.singleton.messages.ContainerNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * Message container which holds node data in {@link SchemaPathMessage}, {@link ContainerNodeMessage} and
 * {@link DOMDataTreeIdentifier} prepared to send between remote hosts with serialization when action operation is
 * invoked.
 */
public class InvokeActionMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final SchemaPathMessage schemaPathMessage;
    private final ContainerNodeMessage containerNodeMessage;
    private final DOMDataTreeIdentifier domDataTreeIdentifier;

    /**
     * Constructor for {@code InvokeActionMessage}.
     *
     * @param schemaPathMessage SchemaPathMessage
     * @param containerNodeMessage ContainerNodeMessage
     * @param domDataTreeIdentifier DOMDataTreeIdentifier
     */
    public InvokeActionMessage(final SchemaPathMessage schemaPathMessage,
        final @Nullable ContainerNodeMessage containerNodeMessage, final DOMDataTreeIdentifier domDataTreeIdentifier) {
        this.schemaPathMessage = requireNonNull(schemaPathMessage);
        this.containerNodeMessage = requireNonNull(containerNodeMessage);
        this.domDataTreeIdentifier = requireNonNull(domDataTreeIdentifier);
    }

    public Absolute getSchemaPath() {
        return schemaPathMessage.getSchemaPath();
    }

    SchemaPathMessage getSchemaPathMessage() {
        return schemaPathMessage;
    }

    public @Nullable ContainerNodeMessage getContainerNodeMessage() {
        return containerNodeMessage;
    }

    public DOMDataTreeIdentifier getDOMDataTreeIdentifier() {
        return domDataTreeIdentifier;
    }

    private Object writeReplace() {
        return new Proxy(this);
    }

    @Override
    public String toString() {
        return "InvokeActionMessage [schemaPathMessage=" + schemaPathMessage + ", containerNodeMessage="
            + containerNodeMessage + ", domDataTreeIdentifier=" + domDataTreeIdentifier + "]";
    }

    private static class Proxy implements Externalizable {
        @Serial
        private static final long serialVersionUID = 2L;

        private InvokeActionMessage invokeActionMessage;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public Proxy() {
            //Due to Externalizable
        }

        Proxy(final InvokeActionMessage invokeActionMessage) {
            this.invokeActionMessage = invokeActionMessage;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeObject(invokeActionMessage.getSchemaPathMessage());
            out.writeObject(invokeActionMessage.getContainerNodeMessage());
            out.writeObject(invokeActionMessage.getDOMDataTreeIdentifier());
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            invokeActionMessage = new InvokeActionMessage((SchemaPathMessage) in.readObject(),
                (ContainerNodeMessage) in.readObject(), (DOMDataTreeIdentifier) in.readObject());
        }

        private Object readResolve() {
            return invokeActionMessage;
        }
    }
}
