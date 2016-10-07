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
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataInput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataOutput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeInputOutput;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;

/**
 * Message which holds node data, prepared to sending between remote hosts with serialization.
 */
public class NormalizedNodeMessage implements Externalizable {
    private static final long serialVersionUID = 1L;

    private YangInstanceIdentifier identifier = null;
    private NormalizedNode<?, ?> node = null;

    public NormalizedNodeMessage() {
        // empty constructor needed for Externalizable
    }

    public NormalizedNodeMessage(final YangInstanceIdentifier identifier, final NormalizedNode<?, ?> node) {
        this.identifier = identifier;
        this.node = node;
    }

    public YangInstanceIdentifier getIdentifier() {
        return identifier;
    }

    public NormalizedNode<?, ?> getNode() {
        return node;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        final NormalizedNodeDataOutput dataOutput = NormalizedNodeInputOutput.newDataOutput(out);
        final NormalizedNodeWriter normalizedNodeWriter =
                NormalizedNodeWriter.forStreamWriter((NormalizedNodeStreamWriter) dataOutput);

        dataOutput.writeYangInstanceIdentifier(identifier);

        normalizedNodeWriter.write(node);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final NormalizedNodeDataInput dataInput = NormalizedNodeInputOutput.newDataInput(in);

        identifier = dataInput.readYangInstanceIdentifier();
        node = dataInput.readNormalizedNode();
    }
}
