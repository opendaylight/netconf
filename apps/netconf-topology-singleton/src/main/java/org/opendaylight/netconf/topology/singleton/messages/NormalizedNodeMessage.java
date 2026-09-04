/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeDataInput;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeStreamVersion;

/**
 * Message which holds node data, prepared to sending between remote hosts with serialization.
 */
public class NormalizedNodeMessage implements Externalizable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private YangInstanceIdentifier identifier;
    private NormalizedNode node;

    public NormalizedNodeMessage() {
        // empty constructor needed for Externalizable
    }

    public NormalizedNodeMessage(final YangInstanceIdentifier identifier, final NormalizedNode node) {
        this.identifier = identifier;
        this.node = node;
    }

    public YangInstanceIdentifier getIdentifier() {
        return identifier;
    }

    public NormalizedNode getNode() {
        return node;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        try (var stream = NormalizedNodeStreamVersion.POTASSIUM.newDataOutput(out)) {
            stream.writeNormalizedNode(node);
            stream.writeYangInstanceIdentifier(getIdentifier());
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        final var stream = NormalizedNodeDataInput.newDataInput(in);
        node = stream.readNormalizedNode();
        identifier = stream.readYangInstanceIdentifier();
    }

    @Override
    public String toString() {
        return "NormalizedNodeMessage [identifier=" + identifier + ", node=" + node + "]";
    }
}
