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
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with depth parameter.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParameterAwareNormalizedNodeWriterDepthTest {

    @Mock
    private NormalizedNodeStreamWriter writer;
    @Mock
    private ContainerNode containerNodeData;
    @Mock
    private MapNode mapNodeData;
    @Mock
    private MapEntryNode mapEntryNodeData;
    @Mock
    private LeafSetNode<String> leafSetNodeData;
    @Mock
    private LeafSetEntryNode<String> leafSetEntryNodeData;
    @Mock
    private LeafNode<String> keyLeafNodeData;
    @Mock
    private LeafNode<String> anotherLeafNodeData;

    private NodeIdentifier containerNodeIdentifier;
    private NodeIdentifier mapNodeIdentifier;
    private NodeIdentifierWithPredicates mapEntryNodeIdentifier;
    private NodeIdentifier leafSetNodeIdentifier;
    private NodeWithValue<?> leafSetEntryNodeIdentifier;
    private NodeIdentifier keyLeafNodeIdentifier;
    private NodeIdentifier anotherLeafNodeIdentifier;

    private Collection<DataContainerChild<?, ?>> containerNodeValue;
    private Collection<MapEntryNode> mapNodeValue;
    private Collection<DataContainerChild<?, ?>> mapEntryNodeValue;
    private Collection<LeafSetEntryNode<String>> leafSetNodeValue;
    private String leafSetEntryNodeValue;
    private String keyLeafNodeValue;
    private String anotherLeafNodeValue;

    @Before
    public void setUp() {
        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);
        when(containerNodeData.getNodeType()).thenReturn(containerNodeIdentifier.getNodeType());

        mapNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "list"));
        when(mapNodeData.getIdentifier()).thenReturn(mapNodeIdentifier);

        final QName leafSetEntryNodeQName = QName.create("namespace", "leaf-set-entry");
        leafSetEntryNodeValue = "leaf-set-value";
        leafSetEntryNodeIdentifier = new NodeWithValue<>(leafSetEntryNodeQName, leafSetEntryNodeValue);
        when(leafSetEntryNodeData.getIdentifier()).thenReturn(leafSetEntryNodeIdentifier);

        leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
        when(leafSetNodeData.getIdentifier()).thenReturn(leafSetNodeIdentifier);

        final QName mapEntryNodeKey = QName.create("namespace", "key-field");
        keyLeafNodeIdentifier = NodeIdentifier.create(mapEntryNodeKey);
        keyLeafNodeValue = "key-value";

        mapEntryNodeIdentifier = NodeIdentifierWithPredicates.of(
                QName.create("namespace", "list-entry"), mapEntryNodeKey, keyLeafNodeValue);
        when(mapEntryNodeData.getIdentifier()).thenReturn(mapEntryNodeIdentifier);
        when(mapEntryNodeData.getChild(keyLeafNodeIdentifier)).thenReturn(Optional.of(keyLeafNodeData));

        when(keyLeafNodeData.getValue()).thenReturn(keyLeafNodeValue);
        when(keyLeafNodeData.getIdentifier()).thenReturn(keyLeafNodeIdentifier);

        anotherLeafNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "another-field"));
        anotherLeafNodeValue = "another-value";

        when(anotherLeafNodeData.getValue()).thenReturn(anotherLeafNodeValue);
        when(anotherLeafNodeData.getIdentifier()).thenReturn(anotherLeafNodeIdentifier);

        // values
        when(leafSetEntryNodeData.getValue()).thenReturn(leafSetEntryNodeValue);

        leafSetNodeValue = Collections.singletonList(leafSetEntryNodeData);
        when(leafSetNodeData.getValue()).thenReturn(leafSetNodeValue);

        containerNodeValue = Collections.singleton(leafSetNodeData);
        when(containerNodeData.getValue()).thenReturn(containerNodeValue);

        mapEntryNodeValue = Sets.newHashSet(keyLeafNodeData, anotherLeafNodeData);
        when(mapEntryNodeData.getValue()).thenReturn(mapEntryNodeValue);

        mapNodeValue = Collections.singleton(mapEntryNodeData);
        when(mapNodeData.getValue()).thenReturn(mapNodeValue);
    }

    /**
     * Test write {@link ContainerNode} with children but write data only to depth 1 (children will not be written).
     * Depth parameter is limited to 1.
     */
    @Test
    public void writeContainerWithoutChildrenDepthTest() throws Exception {
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter
                .forStreamWriter(writer, 1, null);

        parameterWriter.write(containerNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link ContainerNode} with children and write also all its children.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeContainerWithChildrenDepthTest() throws Exception {
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE, null);

        parameterWriter.write(containerNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter
                .forStreamWriter(writer, 1, null);

        parameterWriter.write(mapNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE, null);

        parameterWriter.write(mapNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, 1, null);

        parameterWriter.write(leafSetNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetNode} when all its children will be written.
     * Depth parameter has higher value than maximal children depth.
     */
    @Test
    public void writeLeafSetNodeWithChildrenDepthTest() throws Exception {
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE, null);

        parameterWriter.write(leafSetNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE, null);

        parameterWriter.write(leafSetEntryNodeData);

        final InOrder inOrder = inOrder(writer);
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, false, 1, null);

        parameterWriter.write(mapEntryNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, false, Integer.MAX_VALUE, null);

        parameterWriter.write(mapEntryNodeData);

        // unordered
        verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, true, 1, null);

        parameterWriter.write(mapEntryNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, true, Integer.MAX_VALUE, null);

        parameterWriter.write(mapEntryNodeData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
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