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
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with all parameters.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ParameterAwareNormalizedNodeWriterParametersTest {
    private final String leafSetEntryNodeValue = "leaf-set-value";
    private final NodeIdentifier containerNodeIdentifier =
        NodeIdentifier.create(QName.create("namespace", "container"));
    private final NodeIdentifier leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
    private final NodeWithValue<String> leafSetEntryNodeIdentifier =
        new NodeWithValue<>(QName.create("namespace", "leaf-set-entry"), leafSetEntryNodeValue);

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
    private final ContainerNode rootDataContainerData = Builders.containerBuilder()
        .withNodeIdentifier(NodeIdentifier.create(
            QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data")))
        .withChild(leafSetNodeData)
        .build();

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
    public void writeContainerParameterPrioritiesTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, DepthParam.min(),
            List.of(
                Set.of(leafSetNodeIdentifier.getNodeType()),
                Set.of(leafSetEntryNodeIdentifier.getNodeType())));

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
     * Test write {@link ContainerNode} which represents data at restconf/data root.
     * No parameters are used.
     */
    @Test
    public void writeRootDataTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, null, null);

        parameterWriter.write(rootDataContainerData);

        final var inOrder = inOrder(writer);
        inOrder.verify(writer, times(1)).startLeafSet(leafSetNodeIdentifier, 1);
        inOrder.verify(writer, times(1)).startLeafSetEntryNode(leafSetEntryNodeIdentifier);
        inOrder.verify(writer, times(1)).scalarValue(leafSetEntryNodeValue);
        inOrder.verify(writer, times(2)).endNode();
        verifyNoMoreInteractions(writer);
    }

    @Test
    public void writeEmptyRootContainerTest() throws Exception {
        final var parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(writer, null, null);

        parameterWriter.write(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
            .build());
    }
}
