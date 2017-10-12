/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.writer.custom;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.cli.io.ConsoleIO;
import org.opendaylight.netconf.cli.writer.OutFormatter;
import org.opendaylight.netconf.cli.writer.WriteException;
import org.opendaylight.netconf.cli.writer.impl.AbstractWriter;
import org.opendaylight.netconf.cli.writer.impl.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaUtils;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class DataWriter extends AbstractWriter<DataSchemaNode> {

    private final OutFormatter out;
    private final SchemaContext remoteSchemaContext;

    public DataWriter(final ConsoleIO console, final OutFormatter out, final SchemaContext remoteSchemaContext) {
        super(console);
        this.out = out;
        this.remoteSchemaContext = remoteSchemaContext;
    }

    @Override
    protected void writeInner(final DataSchemaNode dataSchemaNode, final List<NormalizedNode<?, ?>> dataNodes)
            throws IOException, WriteException {
        Preconditions.checkArgument(dataNodes.size() == 1, "Expected only 1 element for data node");
        final NormalizedNode<?, ?> dataNode = dataNodes.get(0);
        Preconditions.checkArgument(dataNode instanceof ContainerNode, "Unexpected node type: %s, should be %s",
            dataNode, ContainerNode.class);

        StringBuilder output = new StringBuilder();
        out.increaseIndent().addStringWithIndent(
            output, dataSchemaNode.getQName().getLocalName()).openComposite(output);
        console.writeLn(output.toString());

        for (final Object childNodeObject : ((DataContainerNode<?>) dataNode).getValue()) {
            final NormalizedNode<?, ?> childNode = (NormalizedNode<?, ?>) childNodeObject;
            final Optional<DataSchemaNode> schemaNode = SchemaUtils.findFirstSchema(childNode.getNodeType(),
                remoteSchemaContext.getDataDefinitions());
            Preconditions.checkState(schemaNode.isPresent(), "Unknown data node %s, not defined in schema",
                childNode.getNodeType());
            new NormalizedNodeWriter(console, out).write(schemaNode.get(), Collections.singletonList(childNode));
        }

        output = new StringBuilder();
        out.decreaseIndent().closeCompositeWithIndent(output);
        console.writeLn(output.toString());
    }
}
