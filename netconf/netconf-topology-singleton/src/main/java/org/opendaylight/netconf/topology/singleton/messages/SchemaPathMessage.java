/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import com.google.common.collect.Iterables;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class SchemaPathMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private SchemaPath schemaPath;

    public SchemaPathMessage(final SchemaPath schemaPath) {
        this.schemaPath = schemaPath;
    }

    public SchemaPath getSchemaPath() {
        return schemaPath;
    }

    private Object writeReplace() {
        return new Proxy(this);
    }

    @Override
    public String toString() {
        return "SchemaPathMessage [schemaPath=" + schemaPath + "]";
    }

    private static class Proxy implements Externalizable {
        private static final long serialVersionUID = 2L;

        private SchemaPathMessage schemaPathMessage;

        Proxy() {
            //due to Externalizable
        }

        Proxy(final SchemaPathMessage schemaPathMessage) {
            this.schemaPathMessage = schemaPathMessage;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeInt(Iterables.size(schemaPathMessage.getSchemaPath().getPathTowardsRoot()));

            for (final QName qualifiedName : schemaPathMessage.getSchemaPath().getPathTowardsRoot()) {
                out.writeObject(qualifiedName);
            }

            out.writeBoolean(schemaPathMessage.getSchemaPath().isAbsolute());
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            final int sizePath = in.readInt();
            final QName[] paths = new QName[sizePath];
            for (int i = 0; i < sizePath; i++) {
                paths[i] = (QName) in.readObject();
            }
            final boolean absolute = in.readBoolean();
            schemaPathMessage = new SchemaPathMessage(SchemaPath.create(absolute, paths));
        }

        private Object readResolve() {
            return schemaPathMessage;
        }
    }

}
