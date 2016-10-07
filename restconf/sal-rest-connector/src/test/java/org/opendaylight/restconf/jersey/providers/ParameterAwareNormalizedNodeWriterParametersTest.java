/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
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
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with all parameters.
 */
public class ParameterAwareNormalizedNodeWriterParametersTest {

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
        MockitoAnnotations.initMocks(this);

        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        Mockito.when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);

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

        anotherLeafNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "another-field"));
        anotherLeafNodeValue = "another-value";

        Mockito.when(anotherLeafNodeData.getValue()).thenReturn(anotherLeafNodeValue);
        Mockito.when(anotherLeafNodeData.getIdentifier()).thenReturn(anotherLeafNodeIdentifier);
        Mockito.when(anotherLeafNodeData.getNodeType()).thenReturn(anotherLeafNodeIdentifier.getNodeType());

        // values
        Mockito.when(leafSetEntryNodeData.getValue()).thenReturn(leafSetEntryNodeValue);

        leafSetNodeValue = Collections.singletonList(leafSetEntryNodeData);
        Mockito.when(leafSetNodeData.getValue()).thenReturn(leafSetNodeValue);

        containerNodeValue = Collections.singleton(leafSetNodeData);
        Mockito.when(containerNodeData.getValue()).thenReturn(containerNodeValue);

        mapEntryNodeValue = Sets.newHashSet(keyLeafNodeData, anotherLeafNodeData);
        Mockito.when(mapEntryNodeData.getValue()).thenReturn(mapEntryNodeValue);

        mapNodeValue = Collections.singleton(mapEntryNodeData);
        Mockito.when(mapNodeData.getValue()).thenReturn(mapNodeValue);
    }
}