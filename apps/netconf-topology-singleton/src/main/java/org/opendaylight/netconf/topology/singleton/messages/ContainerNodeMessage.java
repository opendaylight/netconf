/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import static java.util.Objects.requireNonNull;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeDataInput;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeStreamVersion;

/**
 * Message container which holds node data {@link ContainerNode}, prepared to send between remote hosts with
 * serialization when remote action is invoked.
 */
public class ContainerNodeMessage implements Externalizable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private ContainerNode node;

    public ContainerNodeMessage() {
        // Empty Constructor Needed for Externalizable
    }

    /**
     * Constructor for {@code ContainerNodeMessage}.
     *
     * @param node ContainerNode
     */
    public ContainerNodeMessage(final ContainerNode node) {
        this.node = requireNonNull(node);
    }

    public ContainerNode getNode() {
        return node;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        if (node != null) {
            out.writeBoolean(true);

            try (var stream = NormalizedNodeStreamVersion.POTASSIUM.newDataOutput(out)) {
                stream.writeNormalizedNode(node);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        if (!in.readBoolean()) {
            throw new IOException("Missing node");
        }
        node = ContainerNode.class.cast(NormalizedNodeDataInput.newDataInput(in).readNormalizedNode());
    }

    @Override
    public String toString() {
        return "ContainerNodeMessage [node=" + node + "]";
    }
}
