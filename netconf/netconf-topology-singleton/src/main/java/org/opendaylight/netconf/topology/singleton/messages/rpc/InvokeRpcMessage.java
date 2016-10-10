/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.rpc;

import com.google.common.collect.Iterables;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class InvokeRpcMessage implements Externalizable {
    private static final long serialVersionUID = 1L;


    private SchemaPath schemaPath;
    private NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessage() {
        // due to Externalizable interface
    }

    public InvokeRpcMessage(final SchemaPath schemaPath, final NormalizedNodeMessage normalizedNodeMessage) {
        this.schemaPath = schemaPath;
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    public SchemaPath getSchemaPath() {
        return schemaPath;
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(Iterables.size(schemaPath.getPathTowardsRoot()));

        for (final QName qName : schemaPath.getPathTowardsRoot()) {
            out.writeObject(qName);
        }

        out.writeBoolean(schemaPath.isAbsolute());
        out.writeObject(normalizedNodeMessage);

    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        final int sizePath = in.readInt();
        final QName[] paths = new QName[sizePath];
        for (int i = 0; i < sizePath; i++) {
            paths[i] = (QName) in.readObject();
        }
        final boolean absolute = in.readBoolean();
        normalizedNodeMessage = (NormalizedNodeMessage) in.readObject();
        schemaPath = SchemaPath.create(absolute, paths);
    }
}
