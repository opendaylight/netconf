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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with fields parameter.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParameterAwareNormalizedNodeWriterFieldsTest {
    private final String leafSetEntryNodeValue = "leaf-set-value";
    private final String keyLeafNodeValue = "key-value";
    private final NodeIdentifier containerNodeIdentifier =
        NodeIdentifier.create(QName.create("namespace", "container"));
    private final NodeIdentifier mapNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "list"));
    private final NodeIdentifier leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
    private final NodeWithValue<String> leafSetEntryNodeIdentifier =
        new NodeWithValue<>(QName.create("namespace", "leaf-set-entry"), leafSetEntryNodeValue);
    private final NodeIdentifier keyLeafNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "key-field"));
    private final NodeIdentifierWithPredicates mapEntryNodeIdentifier = NodeIdentifierWithPredicates.of(
        QName.create("namespace", "list-entry"), keyLeafNodeIdentifier.getNodeType(), keyLeafNodeValue);

    private final LeafSetEntryNode<String> leafSetEntryNodeData = Builders.<String>leafSetEntryBuilder()
        .withNodeIdentifier(leafSetEntryNodeIdentifier)
        .withValue(leafSetEntryNodeValue)
        .build();
    private final SystemLeafSetNode<String> leafSetNodeData = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(leafSetNodeIdentifier)
        .withChild(leafSetEntryNodeData)
        .build();
    private final ContainerNode containerNodeData = Builders.containerBuilder()
        .withNodeIdentifier(containerNodeIdentifier)
        .withChild(leafSetNodeData)
        .build();
    private final LeafNode<String> keyLeafNodeData = ImmutableNodes.leafNode(keyLeafNodeIdentifier, keyLeafNodeValue);
    private final MapEntryNode mapEntryNodeData = Builders.mapEntryBuilder()
        .withNodeIdentifier(mapEntryNodeIdentifier)
        .withChild(keyLeafNodeData)
        .build();
    private final SystemMapNode mapNodeData = Builders.mapBuilder()
        .withNodeIdentifier(mapNodeIdentifier)
        .withChild(mapEntryNodeData)
        .build();

    @Mock
    private NormalizedNodeStreamWriter writer;

    /**
     * Test write {@link ContainerNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of container children to be written.
     */
    @Test
    public void writeContainerWithLimitedFieldsTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, null, List.of(Set.of()));

        parameterWriter.write(containerNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startContainerNode(containerNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written.
     * Fields parameter selects 1/1 of container children to be written.
     */
    @Test
    public void writeContainerAllFieldsTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, List.of(Set.of(leafSetNodeIdentifier.getNodeType())));

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
     * Test write {@link MapEntryNode} as child of {@link MapNode} when children which will be written are limited.
     * Fields parameter selects 0/1 of map entry node children to be written.
     */
    @Test
    public void writeMapEntryNodeWithLimitedFieldsTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, null, List.of(Set.of()));

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 1);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    /**
     * Test write {@link MapEntryNode} as child of {@link MapNode} when all its children will be written.
     * Fields parameter selects 1/1 of map entry node children to be written.
     */
    @Test
    public void writeMapNodeAllFieldsTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, null,
            List.of(Set.of(keyLeafNodeData.name().getNodeType())));

        parameterWriter.write(mapNodeData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startMapNode(mapNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startMapEntryNode(mapEntryNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startLeafNode(keyLeafNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(keyLeafNodeValue);
        inOrder.verify(writer, times(3)).endNode();
        verifyNoMoreInteractions(writer);
    }
}