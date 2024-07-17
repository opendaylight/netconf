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
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * Unit test for {@link NormalizedNodeWriter} used with depth parameter.
 */
@ExtendWith(MockitoExtension.class)
class NormalizedNodeWriterDepthTest extends AbstractNormalizedNodeWriterTest {
    private final String anotherLeafNodeValue = "another-value";
    private final NodeIdentifier anotherLeafNodeIdentifier =
        NodeIdentifier.create(QName.create("namespace", "another-field"));

    private final MapEntryNode mapEntryNodeData = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(mapEntryNodeIdentifier)
        .withChild(keyLeafNodeData)
        .withChild(ImmutableNodes.leafNode(anotherLeafNodeIdentifier, anotherLeafNodeValue))
        .build();
    private final SystemMapNode mapNodeData = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(LIST_NID)
        .withChild(mapEntryNodeData)
        .build();

    @Mock
    private NormalizedNodeStreamWriter writer;

    /**
     * Test write {@link ContainerNode} with children but write data only to depth 1 (children will not be written).
     * Depth parameter is limited to 1.
     */
    @Test
    void writeContainerWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.min());

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startContainerNode(CONTAINER_NID, 1);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write {@link ContainerNode} with children and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    void writeContainerWithChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.max());

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startContainerNode(CONTAINER_NID, 1);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(3)).endNode();
    }

    /**
     * Test write with {@link MapNode} with children but write data only to depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    void writeMapNodeWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.min());

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapNode(LIST_NID, 1);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer, times(2)).endNode();
    }

    /**
     * Test write {@link MapNode} with children and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    @Disabled("FIXME: Although ordered writer is used leaves are not written in expected order")
    void writeMapNodeWithChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.max());

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapNode(LIST_NID, 1);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer).startLeafNode(KEY_FIELD_NID);
        inOrder.verify(writer).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer).endNode();
        inOrder.verify(writer).startLeafNode(KEY_FIELD_NID);
        inOrder.verify(writer).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        // FIXME this assertion is not working because leaves are not written in expected order
        inOrder.verify(writer).startLeafNode(anotherLeafNodeIdentifier);
        inOrder.verify(writer).scalarValue(anotherLeafNodeValue);
        inOrder.verify(writer, times(3)).endNode();
    }

    /**
     * Test write with {@link LeafSetNode} with depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    void writeLeafSetNodeWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.min());

        parameterWriter.write(leafSetNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write with {@link LeafSetNode} when all its children will be written.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    void writeLeafSetNodeWithChildrenDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.max());

        parameterWriter.write(leafSetNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startLeafSet(LEAF_SET_NID, 1);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(2)).endNode();
    }

    /**
     * Test write with {@link LeafSetEntryNode}.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    void writeLeafSetEntryNodeDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.max());

        parameterWriter.write(leafSetEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write with {@link MapEntryNode} unordered to depth 1 to write only keys.
     * Depth parameter limits depth to 1.
     */
    @Test
    void writeMapEntryNodeUnorderedOnlyKeysDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, false, DepthParam.min(), null);

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write with {@link MapEntryNode} unordered with full depth.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    void writeMapEntryNodeUnorderedDepthTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, false, DepthParam.max(), null);

        parameterWriter.write(mapEntryNodeData);

        // unordered
        verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        verify(writer).startLeafNode(KEY_FIELD_NID);
        verify(writer).scalarValue(keyLeafNodeValue);
        verify(writer).startLeafNode(anotherLeafNodeIdentifier);
        verify(writer).scalarValue(anotherLeafNodeValue);
        verify(writer, times(3)).endNode();
    }

    /**
     * Test write with {@link MapEntryNode} ordered with depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    void writeMapEntryNodeOrderedWithoutChildrenTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.min());

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer).endNode();
    }

    /**
     * Test write with {@link MapEntryNode} ordered and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    @Disabled("FIXME: Although ordered writer is used leaves are not written in expected order")
    void writeMapEntryNodeOrderedTest() throws Exception {
        final var parameterWriter = NormalizedNodeWriter.forStreamWriter(writer, DepthParam.max());

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer).startLeafNode(KEY_FIELD_NID);
        inOrder.verify(writer).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer).endNode();
        inOrder.verify(writer).startLeafNode(KEY_FIELD_NID);
        inOrder.verify(writer).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer).endNode();
        // FIXME this assertion is not working because leaves are not written in expected order
        inOrder.verify(writer).startLeafNode(anotherLeafNodeIdentifier);
        inOrder.verify(writer).scalarValue(anotherLeafNodeValue);
        inOrder.verify(writer, times(2)).endNode();
    }
}