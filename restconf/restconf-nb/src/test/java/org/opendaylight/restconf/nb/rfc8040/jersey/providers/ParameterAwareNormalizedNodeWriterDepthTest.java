/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with depth parameter.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParameterAwareNormalizedNodeWriterDepthTest {
    private final String leafSetEntryNodeValue = "leaf-set-value";
    private final String keyLeafNodeValue = "key-value";
    private final String anotherLeafNodeValue = "another-value";

    private final NodeIdentifier containerNodeIdentifier =
        NodeIdentifier.create(QName.create("namespace", "container"));
    private final NodeIdentifier mapNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "list"));
    private final NodeIdentifier keyLeafNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "key-field"));
    private final NodeWithValue<String> leafSetEntryNodeIdentifier =
        new NodeWithValue<>(QName.create("namespace", "leaf-set-entry"), leafSetEntryNodeValue);
    private final NodeIdentifier leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
    private final NodeIdentifierWithPredicates mapEntryNodeIdentifier = NodeIdentifierWithPredicates.of(
        QName.create("namespace", "list-entry"), keyLeafNodeIdentifier.getNodeType(), keyLeafNodeValue);
    private final NodeIdentifier anotherLeafNodeIdentifier =
        NodeIdentifier.create(QName.create("namespace", "another-field"));

    private final LeafNode<String> keyLeafNodeData = ImmutableNodes.leafNode(keyLeafNodeIdentifier, keyLeafNodeValue);
    private final LeafNode<String> anotherLeafNodeData =
        ImmutableNodes.leafNode(anotherLeafNodeIdentifier, anotherLeafNodeValue);
    private final LeafSetEntryNode<String> leafSetEntryNodeData = Builders.<String>leafSetEntryBuilder()
        .withNodeIdentifier(leafSetEntryNodeIdentifier)
        .withValue(leafSetEntryNodeValue)
        .build();
    private final SystemLeafSetNode<String> leafSetNodeData = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(leafSetNodeIdentifier)
        .withChild(leafSetEntryNodeData)
        .build();
    private final MapEntryNode mapEntryNodeData = Builders.mapEntryBuilder()
        .withNodeIdentifier(mapEntryNodeIdentifier)
        .withChild(keyLeafNodeData)
        .withChild(anotherLeafNodeData)
        .build();
    private final SystemMapNode mapNodeData = Builders.mapBuilder()
        .withNodeIdentifier(mapNodeIdentifier)
        .withChild(mapEntryNodeData)
        .build();
    private final ContainerNode containerNodeData = Builders.containerBuilder()
        .withNodeIdentifier(containerNodeIdentifier)
        .withChild(leafSetNodeData)
        .build();

    @Mock
    private NormalizedNodeStreamWriter writer;

    /**
     * Test write {@link ContainerNode} with children but write data only to depth 1 (children will not be written).
     * Depth parameter is limited to 1.
     */
    @Test
    public void writeContainerWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.min(), null);

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startContainerNode(containerNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link ContainerNode} with children and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeContainerWithChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.max(), null);

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startContainerNode(containerNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(3)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapNode} with children but write data only to depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    public void writeMapNodeWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.min(), null);

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link MapNode} with children and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     * FIXME
     * Although ordered writer is used leaves are not written in expected order.
     *
     */
    @Ignore
    @Test
    public void writeMapNodeWithChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.max(), null);

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer, times(2)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(1)).endNode();
        inOrder.verify(writer, times(2)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(2)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        // FIXME this assertion is not working because leaves are not written in expected order
        inOrder.verify(writer, times(1)).startLeafNode(anotherLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(anotherLeafNodeValue);
        inOrder.verify(writer, times(3)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetNode} with depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    public void writeLeafSetNodeWithoutChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.min(), null);

        parameterWriter.write(leafSetNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetNode} when all its children will be written.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeLeafSetNodeWithChildrenDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.max(), null);

        parameterWriter.write(leafSetNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetEntryNode}.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeLeafSetEntryNodeDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.max(), null);

        parameterWriter.write(leafSetEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} unordered to depth 1 to write only keys.
     * Depth parameter limits depth to 1.
     */
    @Test
    public void writeMapEntryNodeUnorderedOnlyKeysDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, false, DepthParam.min(),
            null);

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        // write only the key
        inOrder.verify(writer, times(1)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} unordered with full depth.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeMapEntryNodeUnorderedDepthTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, false, DepthParam.max(),
            null);

        parameterWriter.write(mapEntryNodeData);

        // unordered
        verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        verify(writer, times(1)).startLeafNode(keyLeafNodeIdentifier);
        verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        verify(writer, times(1)).startLeafNode(anotherLeafNodeIdentifier);
        verify(writer, times(1)).scalarValue(anotherLeafNodeValue);
        verify(writer, times(3)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} ordered with depth 1 (children will not be written).
     * Depth parameter limits depth to 1.
     */
    @Test
    public void writeMapEntryNodeOrderedWithoutChildrenTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, true, DepthParam.min(),
            null);

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} ordered and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     * FIXME
     * Although ordered writer is used leaves are not written in expected order.
     *
     */
    @Ignore
    @Test
    public void writeMapEntryNodeOrderedTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, true, DepthParam.max(),
            null);

        parameterWriter.write(mapEntryNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 2);
        inOrder.verify(writer, times(1)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(1)).endNode();
        inOrder.verify(writer, times(1)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(1)).endNode();
        // FIXME this assertion is not working because leaves are not written in expected order
        inOrder.verify(writer, times(1)).startLeafNode(anotherLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(anotherLeafNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }
}