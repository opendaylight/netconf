/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
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
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.SerializationUtils;

public class ContainerNodeMessage implements Externalizable {
    private static final long serialVersionUID = 1L;

    private ContainerNode node;
    private YangInstanceIdentifier identifier;

    public ContainerNodeMessage() {
        // Empty Constructor Needed for Externalizable
    }

    /**
     * Constructor for {@code ContainerNodeMessage}.
     *
     * @param identifier YangInstanceIdentifier
     * @param node ContainerNode
     */
    public ContainerNodeMessage(final YangInstanceIdentifier identifier, final ContainerNode node) {
        this.identifier = identifier;
        this.node = node;
    }

    public YangInstanceIdentifier getIdentifier() {
        return identifier;
    }

    public ContainerNode getNode() {
        return node;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        SerializationUtils.writeNodeAndPath(out, getIdentifier(), node);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        SerializationUtils.readNodeAndPath(in, this, APPLIER);
    }

    @Override
    public String toString() {
        return "ContainerNodeMessage [identifier=" + identifier + ", node=" + node + "]";
    }

    private static final SerializationUtils.Applier<ContainerNodeMessage> APPLIER = (instance, path, node) -> {
        instance.identifier = path;
        instance.node = (ContainerNode)node;
    };
}