/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit test for {@link NormalizedNodeWriter} used with all parameters.
 */
@ExtendWith(MockitoExtension.class)
class NormalizedNodeWriterParametersTest extends AbstractNormalizedNodeWriterTest {
    @Mock
    private NormalizedNodeStreamWriter writer;

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written by fields parameter.
     * Depth parameter is also used and limits output to depth 1.
     * Fields parameter has effect limiting depth parameter in the way that selected nodes and its ancestors are
     * written regardless of their depth (some of container children have depth > 1).
     * Fields parameter selects all container children to be written and also all children of those children.
     */
    @Test
    void writeContainerParameterPrioritiesTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.min(),
            List.of(
                Set.of(LEAF_SET_NID.getNodeType()),
                Set.of(leafSetEntryNodeIdentifier.getNodeType())));

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startContainerNode(CONTAINER_NID, 1);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(3)).endNode();
    }

    /**
     * Test write {@link ContainerNode} which represents data at restconf/data root.
     * No parameters are used.
     */
    @Test
    void writeRootDataTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null);

        parameterWriter.write(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(
                QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data")))
            .withChild(leafSetNodeData)
            .build());

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(2)).endNode();
    }

    @Test
    void writeEmptyRootContainerTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null);

        parameterWriter.write(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
            .build());
    }
}
