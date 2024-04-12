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
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * Unit test for {@link NormalizedNodeWriter} used with fields parameter.
 */
@ExtendWith(MockitoExtension.class)
class NormalizedNodeWriterFieldsTest extends AbstractNormalizedNodeWriterTest {
    private final SystemMapNode mapNodeData = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(LIST_NID)
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(mapEntryNodeIdentifier)
            .withChild(keyLeafNodeData)
            .build())
        .build();

    @Mock
    private NormalizedNodeStreamWriter writer;

    /**
     * Test write {@link ContainerNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of container children to be written.
     */
    @Test
    void writeContainerWithLimitedFieldsTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null, List.of(Set.of()));

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startContainerNode(CONTAINER_NID, 1);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written.
     * Fields parameter selects 1/1 of container children to be written.
     */
    @Test
    void writeContainerAllFieldsTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null,
            List.of(Set.of(LEAF_SET_NID.getNodeType())));

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startContainerNode(CONTAINER_NID, 1);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(3)).endNode();
    }

    /**
     * Test write {@link MapEntryNode} as child of {@link MapNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of map entry node children to be written.
     */
    @Test
    void writeMapEntryNodeWithLimitedFieldsTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null, List.of(Set.of()));

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapNode(LIST_NID, 1);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 1);
        inOrder.verify(writer, times(2)).endNode();
    }

    /**
     * Test write {@link MapEntryNode} as child of {@link MapNode} when all its children will be written.
     * Fields parameter selects 1/1 of map entry node children to be written.
     */
    @Test
    void writeMapNodeAllFieldsTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, null,
            List.of(Set.of(keyLeafNodeData.name().getNodeType())));

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapNode(LIST_NID, 1);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 1);
        inOrder.verify(writer).startLeafNode(KEY_FIELD_NID);
        inOrder.verify(writer).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(3)).endNode();
    }
}