/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.messages;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataInput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataOutput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeInputOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

abstract class AbstractMessage<T> implements Externalizable {

    private transient SchemaPath path;
    private transient T content;

    public AbstractMessage() {
    }

    public AbstractMessage(final SchemaPath path, final T content) {
        this.path = path;
        this.content = content;
    }

    public SchemaPath getSchemaPath() {
        return path;
    }

    public T getContent() {
        return content;
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final NormalizedNodeDataInput streamReader = NormalizedNodeInputOutput.newDataInput(in);

        content = (T) streamReader.readNormalizedNode();
        final Iterable<QName> readPath = (Iterable<QName>) in.readObject();
        this.path = SchemaPath.create(readPath, true);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        final NormalizedNodeDataOutput output = NormalizedNodeInputOutput.newDataOutput(out);

        output.writeNormalizedNode((NormalizedNode<?, ?>) content);
        out.writeObject(path.getPathFromRoot());
        out.flush();
        out.close();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSchemaPath(), getContent());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractMessage that = (AbstractMessage) o;
        return Objects.equals(this.getSchemaPath(), that.getSchemaPath()) &&
                Objects.equals(getContent(), that.getContent());
    }

}
