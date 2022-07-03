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
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.nb.rfc8040.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with all parameters.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParameterAwareNormalizedNodeWriterParametersTest {
    @Mock
    private NormalizedNodeStreamWriter writer;
    @Mock
    private ContainerNode containerNodeData;
    @Mock
    private SystemLeafSetNode<String> leafSetNodeData;
    @Mock
    private LeafSetEntryNode<String> leafSetEntryNodeData;
    @Mock
    private ContainerNode rootDataContainerData;

    private NodeIdentifier containerNodeIdentifier;
    private NodeIdentifier leafSetNodeIdentifier;
    private NodeWithValue<String> leafSetEntryNodeIdentifier;

    private Collection<DataContainerChild> containerNodeValue;
    private Collection<LeafSetEntryNode<String>> leafSetNodeValue;
    private String leafSetEntryNodeValue;
    private Collection<DataContainerChild> rootDataContainerValue;

    @Before
    public void setUp() {
        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);

        final QName leafSetEntryNodeQName = QName.create("namespace", "leaf-set-entry");
        leafSetEntryNodeValue = "leaf-set-value";
        leafSetEntryNodeIdentifier = new NodeWithValue<>(leafSetEntryNodeQName, leafSetEntryNodeValue);
        when(leafSetEntryNodeData.getIdentifier()).thenReturn(leafSetEntryNodeIdentifier);

        leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
        when(leafSetNodeData.getIdentifier()).thenReturn(leafSetNodeIdentifier);

        // values
        when(leafSetEntryNodeData.body()).thenReturn(leafSetEntryNodeValue);

        leafSetNodeValue = List.of(leafSetEntryNodeData);
        when(leafSetNodeData.body()).thenReturn(leafSetNodeValue);

        containerNodeValue = Set.of(leafSetNodeData);
        when(containerNodeData.body()).thenReturn(containerNodeValue);

        rootDataContainerValue = Set.of(leafSetNodeData);
        when(rootDataContainerData.getIdentifier()).thenReturn(NodeIdentifier.create(
            QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data")));
        when(rootDataContainerData.body()).thenReturn(rootDataContainerValue);
    }

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written by fields parameter.
     * Depth parameter is also used and limits output to depth 1.
     * Fields parameter has effect limiting depth parameter in the way that selected nodes and its ancestors are
     * written regardless of their depth (some of container children have depth > 1).
     * Fields parameter selects all container children to be written and also all children of those children.
     */
    @Test
    public void writeContainerParameterPrioritiesTest() throws Exception {
        final List<Set<QName>> limitedFields = List.of(
            Set.of(leafSetNodeIdentifier.getNodeType()),
            Set.of(leafSetEntryNodeIdentifier.getNodeType()));

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, DepthParam.min(), limitedFields);

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
     * Test write {@link ContainerNode} which represents data at restconf/data root.
     * No parameters are used.
     */
    @Test
    public void writeRootDataTest() throws Exception {
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, null);

        parameterWriter.write(rootDataContainerData);

        final InOrder inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
        inOrder.verify(writer, times(1)).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    @Test
    public void writeEmptyRootContainerTest() throws Exception {
        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, null);

        parameterWriter.write(ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME)).build());
    }
}