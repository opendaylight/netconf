/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util.messages;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeInputStreamReader;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeOutputStreamWriter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;

public class NormalizedNodeMessage implements Externalizable{
    private static final long serialVersionUID = 1L;

    private YangInstanceIdentifier identifier = null;
    private NormalizedNode<?, ?> node = null;

    public NormalizedNodeMessage() {

    }

    public NormalizedNodeMessage(YangInstanceIdentifier identifier, NormalizedNode<?, ?> node) {
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
    public void writeExternal(ObjectOutput out) throws IOException {
        final NormalizedNodeOutputStreamWriter streamWriter = new NormalizedNodeOutputStreamWriter(out);
        final NormalizedNodeWriter normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(streamWriter);

        streamWriter.writeYangInstanceIdentifier(identifier);
        normalizedNodeWriter.write(node);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        final NormalizedNodeInputStreamReader streamReader = new NormalizedNodeInputStreamReader(in);

        identifier = streamReader.readYangInstanceIdentifier();
        node = streamReader.readNormalizedNode();
    }
}
