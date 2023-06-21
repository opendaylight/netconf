/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class SchemaPathMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings("SE_BAD_FIELD")
    private final Absolute schemaPath;

    public SchemaPathMessage(final QName qname) {
        this(Absolute.of(qname));
    }

    public SchemaPathMessage(final Absolute schemaPath) {
        this.schemaPath = schemaPath;
    }

    public Absolute getSchemaPath() {
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
        @Serial
        private static final long serialVersionUID = 2L;

        private SchemaPathMessage schemaPathMessage;

        @SuppressWarnings("checkstyle:RedundantModifier")
        public Proxy() {
            //due to Externalizable
        }

        Proxy(final SchemaPathMessage schemaPathMessage) {
            this.schemaPathMessage = schemaPathMessage;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            final List<QName> path = schemaPathMessage.getSchemaPath().getNodeIdentifiers();
            out.writeInt(path.size());
            for (final QName qualifiedName : path) {
                // FIXME: switch to QName.writeTo() or a sal-clustering-commons stream
                out.writeObject(qualifiedName);
            }

            out.writeBoolean(true);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            final int sizePath = in.readInt();
            final QName[] paths = new QName[sizePath];
            for (int i = 0; i < sizePath; i++) {
                // FIXME: switch to QName.readFrom() or a sal-clustering-commons stream
                paths[i] = (QName) in.readObject();
            }
            final boolean absolute = in.readBoolean();
            if (!absolute) {
                throw new InvalidObjectException("Non-absolute path");
            }
            schemaPathMessage = new SchemaPathMessage(Absolute.of(paths));
        }

        private Object readResolve() {
            return schemaPathMessage;
        }
    }

}
