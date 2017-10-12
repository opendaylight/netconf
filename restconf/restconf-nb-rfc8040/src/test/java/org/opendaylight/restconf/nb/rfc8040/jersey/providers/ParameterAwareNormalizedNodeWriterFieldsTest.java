/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with fields parameter.
 */
public class ParameterAwareNormalizedNodeWriterFieldsTest {

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

    private NodeIdentifier containerNodeIdentifier;
    private NodeIdentifier mapNodeIdentifier;
    private NodeIdentifierWithPredicates mapEntryNodeIdentifier;
    private NodeIdentifier leafSetNodeIdentifier;
    private NodeWithValue<?> leafSetEntryNodeIdentifier;
    private NodeIdentifier keyLeafNodeIdentifier;

    private Collection<DataContainerChild<?, ?>> containerNodeValue;
    private Collection<MapEntryNode> mapNodeValue;
    private Collection<DataContainerChild<?, ?>> mapEntryNodeValue;
    private Collection<LeafSetEntryNode<String>> leafSetNodeValue;
    private String leafSetEntryNodeValue;
    private String keyLeafNodeValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        Mockito.when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);
        Mockito.when(containerNodeData.getNodeType()).thenReturn(containerNodeIdentifier.getNodeType());

        mapNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "list"));
        Mockito.when(mapNodeData.getIdentifier()).thenReturn(mapNodeIdentifier);
        Mockito.when(mapNodeData.getNodeType()).thenReturn(mapNodeIdentifier.getNodeType());

        final QName leafSetEntryNodeQName = QName.create("namespace", "leaf-set-entry");
        leafSetEntryNodeValue = "leaf-set-value";
        leafSetEntryNodeIdentifier = new NodeWithValue<>(leafSetEntryNodeQName, leafSetEntryNodeValue);
        Mockito.when(leafSetEntryNodeData.getIdentifier()).thenReturn(leafSetEntryNodeIdentifier);
        Mockito.when(leafSetEntryNodeData.getNodeType()).thenReturn(leafSetEntryNodeIdentifier.getNodeType());
        Mockito.when(leafSetEntryNodeData.getNodeType()).thenReturn(leafSetEntryNodeQName);

        leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
        Mockito.when(leafSetNodeData.getIdentifier()).thenReturn(leafSetNodeIdentifier);
        Mockito.when(leafSetNodeData.getNodeType()).thenReturn(leafSetNodeIdentifier.getNodeType());

        final QName mapEntryNodeKey = QName.create("namespace", "key-field");
        keyLeafNodeIdentifier = NodeIdentifier.create(mapEntryNodeKey);
        keyLeafNodeValue = "key-value";

        mapEntryNodeIdentifier = new NodeIdentifierWithPredicates(
                QName.create("namespace", "list-entry"), Collections.singletonMap(mapEntryNodeKey, keyLeafNodeValue));
        Mockito.when(mapEntryNodeData.getIdentifier()).thenReturn(mapEntryNodeIdentifier);
        Mockito.when(mapEntryNodeData.getNodeType()).thenReturn(mapEntryNodeIdentifier.getNodeType());
        Mockito.when(mapEntryNodeData.getChild(keyLeafNodeIdentifier)).thenReturn(Optional.of(keyLeafNodeData));

        Mockito.when(keyLeafNodeData.getValue()).thenReturn(keyLeafNodeValue);
        Mockito.when(keyLeafNodeData.getIdentifier()).thenReturn(keyLeafNodeIdentifier);
        Mockito.when(keyLeafNodeData.getNodeType()).thenReturn(keyLeafNodeIdentifier.getNodeType());

        // values
        Mockito.when(leafSetEntryNodeData.getValue()).thenReturn(leafSetEntryNodeValue);

        leafSetNodeValue = Collections.singletonList(leafSetEntryNodeData);
        Mockito.when(leafSetNodeData.getValue()).thenReturn(leafSetNodeValue);

        containerNodeValue = Collections.singleton(leafSetNodeData);
        Mockito.when(containerNodeData.getValue()).thenReturn(containerNodeValue);

        mapEntryNodeValue = Sets.newHashSet(keyLeafNodeData);
        Mockito.when(mapEntryNodeData.getValue()).thenReturn(mapEntryNodeValue);

        mapNodeValue = Collections.singleton(mapEntryNodeData);
        Mockito.when(mapNodeData.getValue()).thenReturn(mapNodeValue);
    }

    /**
     * Test write {@link ContainerNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of container children to be written.
     */
    @Test
    public void writeContainerWithLimitedFieldsTest() throws Exception {
        final List<Set<QName>> limitedFields = new ArrayList<>();
        limitedFields.add(Sets.newHashSet());

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, limitedFields);

        parameterWriter.write(containerNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written.
     * Fields parameter selects 1/1 of container children to be written.
     */
    @Test
    public void writeContainerAllFieldsTest() throws Exception {
        final List<Set<QName>> limitedFields = new ArrayList<>();
        limitedFields.add(Sets.newHashSet(leafSetNodeIdentifier.getNodeType()));

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, limitedFields);

        parameterWriter.write(containerNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafSetEntryNode(
                leafSetEntryNodeIdentifier.getNodeType(), leafSetEntryNodeValue);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link MapEntryNode} as child of {@link MapNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of map entry node children to be written.
     */
    @Test
    public void writeMapEntryNodeWithLimitedFieldsTest() throws Exception {
        final List<Set<QName>> limitedFields = new ArrayList<>();
        limitedFields.add(Sets.newHashSet());

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, limitedFields);

        parameterWriter.write(mapNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link MapEntryNode} as child of {@link MapNode} when all its children will be written.
     * Fields parameter selects 1/1 of map entry node children to be written.
     */
    @Test
    public void writeMapNodeAllFieldsTest() throws Exception {
        final List<Set<QName>> limitedFields = new ArrayList<>();
        limitedFields.add(Sets.newHashSet(keyLeafNodeData.getNodeType()));

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, limitedFields);

        parameterWriter.write(mapNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startMapNode(mapNodeIdentifier, mapNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startMapEntryNode(mapEntryNodeIdentifier, mapEntryNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafNode(keyLeafNodeIdentifier, keyLeafNodeValue);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }
}