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
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class InvokeRpcMessage implements Externalizable {
    private static final long serialVersionUID = 1L;

    private NormalizedNodeMessage normalizedNodeMessage;
    private SchemaPathMessage schemaPathMessage;

    // Default constructor for deserialization
    public InvokeRpcMessage(){

    }

    public InvokeRpcMessage(final SchemaPathMessage schemaPathMessage,
                            final NormalizedNodeMessage normalizedNodeMessage) {
        this.schemaPathMessage = schemaPathMessage;
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    public SchemaPath getSchemaPath() {
        return schemaPathMessage.getSchemaPath();
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(schemaPathMessage);
        out.writeObject(normalizedNodeMessage);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.schemaPathMessage = (SchemaPathMessage) in.readObject();
        this.normalizedNodeMessage = (NormalizedNodeMessage) in.readObject();
    }
}
