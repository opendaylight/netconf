/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

public class DepthAwareNormalizedNodeWriterTest {

    @Mock
    private NormalizedNodeStreamWriter writer;
    @Mock
    private ContainerNode data;
    @Mock
    private LeafSetNode<?> child;

    private Collection<DataContainerChild<?, ?>> value;
    private NodeIdentifier containerIdentifier;
    private NodeIdentifier leafSetIdentifier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        containerIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        Mockito.when(data.getIdentifier()).thenReturn(containerIdentifier);

        value = Collections.singleton(child);
        Mockito.when(data.getValue()).thenReturn(value);

        leafSetIdentifier = NodeIdentifier.create(QName.create("namespace", "leafSetNode"));
        Mockito.when(child.getIdentifier()).thenReturn(leafSetIdentifier);
    }

    /**
     * Test depth writer, write data only to depth 1
     */
    @Test
    public void writeWithoutChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 1);

        depthWriter.write(data);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerIdentifier, value.size());
        inOrder.verify(writer, Mockito.times(1)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }

    /**
     * Test depth writer, write data to depth 2
     */
    @Test
    public void writeChildrenTest() throws Exception {
        final DepthAwareNormalizedNodeWriter depthWriter = DepthAwareNormalizedNodeWriter.forStreamWriter(writer, 2);

        depthWriter.write(data);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerIdentifier, value.size());
        inOrder.verify(writer, Mockito.times(1)).startLeafSet(leafSetIdentifier, 0);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }
}