/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Optional;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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

public class DepthAwareNormalizedNodeWriterTest {

    @Mock
    private NormalizedNodeStreamWriter writer;
    @Mock
    private ContainerNode containerNodeData;
    @Mock
    private MapNode mapNodeData;
    @Mock
    private MapEntryNode mapEntryNodeData;
    @Mock
    private LeafSetNode<?> leafSetNodeData;
    @Mock
    private LeafSetEntryNode<?> leafSetEntryNodeData;
    @Mock
    private LeafNode<String> leafData;

    private NodeIdentifier containerNodeIdentifier;
    private NodeIdentifier mapNodeIdentifier;
    private NodeIdentifierWithPredicates mapEntryNodeIdentifier;
    private NodeIdentifier leafSetNodeIdentifier;
    private NodeWithValue<?> leafSetEntryNodeIdentifier;
    private NodeIdentifier leafDataIdentifier;
    private NodeIdentifier mapEntryNodeItemIdentifier;

    private Collection<DataContainerChild<?, ?>> containerNodeValue;
    private Collection<MapEntryNode> mapNodeValue;
    private Collection<DataContainerChild<?, ?>> mapEntryNodeValue;
    private String leafDataValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        Mockito.when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);

        mapNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "list"));
        Mockito.when(mapNodeData.getIdentifier()).thenReturn(mapNodeIdentifier);

        final QName leafSetEntryNodeQName = QName.create("namespace", "leaf-set");
        leafSetEntryNodeIdentifier = new NodeWithValue<>(leafSetEntryNodeQName, "value");
        Mockito.when(leafSetEntryNodeData.getIdentifier()).thenReturn(leafSetEntryNodeIdentifier);
        Mockito.when(leafSetEntryNodeData.getNodeType()).thenReturn(leafSetEntryNodeQName);

        leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leafSetNode"));
        Mockito.when(leafSetNodeData.getIdentifier()).thenReturn(leafSetNodeIdentifier);

        final QName mapEntryNodeKey = QName.create("namespace", "key");
        mapEntryNodeItemIdentifier = NodeIdentifier.create(mapEntryNodeKey);

        leafDataIdentifier = NodeIdentifier.create(mapEntryNodeKey);
        leafDataValue = "value";

        mapEntryNodeIdentifier = new YangInstanceIdentifier.NodeIdentifierWithPredicates(
                QName.create("namespace", "list-entry"), Collections.singletonMap(mapEntryNodeKey, leafDataValue));
        Mockito.when(mapEntryNodeData.getIdentifier()).thenReturn(mapEntryNodeIdentifier);
        Mockito.when(mapEntryNodeData.getChild(leafDataIdentifier)).thenReturn(Optional.of(leafData));
        Mockito.when(leafData.getValue()).thenReturn(leafDataValue);

        Mockito.when(leafData.getIdentifier()).thenReturn(leafDataIdentifier);

        // values
        containerNodeValue = Collections.singleton(leafSetNodeData);
        Mockito.when(containerNodeData.getValue()).thenReturn(containerNodeValue);

        mapNodeValue = Collections.singleton(mapEntryNodeData);
        Mockito.when(mapNodeData.getValue()).thenReturn(mapNodeValue);

        mapEntryNodeValue = Collections.singletonList(leafData);
        Mockito.when(mapEntryNodeData.getValue()).thenReturn(mapEntryNodeValue);
    }

    /**
     * Test write {@link ContainerNode} with children but write data only to depth 1 (children will not be written).
     */
    @Test
    public void writeContainerWithoutChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 1);

        depthWriter.write(containerNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link ContainerNode} with children and write also its children (depth 2).
     */
    @Test
    public void writeContainerWithChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 2);

        depthWriter.write(containerNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startLeafSet(leafSetNodeIdentifier, 0);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapNode} with children but write data only to depth 1 (children will not be written).
     */
    @Test
    public void writeMapNodeWithoutChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 1);

        depthWriter.write(mapNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafNode(leafDataIdentifier, leafDataValue);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link MapNode} with children and write also all its children (depth 2).
     */
    @Test
    public void writeMapNodeWithChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 2);

        depthWriter.write(mapNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(2)).leafNode(leafDataIdentifier, leafDataValue);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetNode}.
     */
    @Test
    public void writeLeafSetNodeTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE);

        depthWriter.write(leafSetNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startLeafSet(leafSetNodeIdentifier, 0);
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link LeafSetEntryNode}.
     */
    @Test
    public void writeLeafSetEntryNodeTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(
                writer, Integer.MAX_VALUE);

        depthWriter.write(leafSetEntryNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).leafSetEntryNode(leafSetEntryNodeIdentifier.getNodeType(), null);
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} unordered.
     */
    @Test
    public void writeMapEntryNodeUnorderedTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(
                writer, false, Integer.MAX_VALUE);

        depthWriter.write(mapEntryNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafNode(leafDataIdentifier, leafDataValue);
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} unordered to depth 1 to write only keys.
     */
    @Test
    public void writeMapEntryNodeUnorderedOnlyKeysTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(
                writer, false, 1);

        depthWriter.write(mapEntryNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafNode(mapEntryNodeItemIdentifier, leafDataValue);
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write with {@link MapEntryNode} ordered.
     */
    @Test
    public void writeMapEntryNodeOrderedTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(
                writer, true, 1);

        depthWriter.write(mapEntryNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafNode(leafDataIdentifier, leafDataValue);
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }
}