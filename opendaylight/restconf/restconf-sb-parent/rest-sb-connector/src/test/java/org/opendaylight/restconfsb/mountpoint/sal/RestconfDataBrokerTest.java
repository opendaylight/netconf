/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class RestconfDataBrokerTest {

    @Mock
    private RestconfFacade facade;
    private YangInstanceIdentifier id;
    private RestconfDataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        id = YangInstanceIdentifier.builder()
                .node(QName.create("ns1", "2016-01-02", "name1"))
                .build();
        doReturn(Futures.immediateFuture(null)).when(facade).getData(LogicalDatastoreType.CONFIGURATION, id);
        doReturn(Futures.immediateFuture(null)).when(facade).headData(LogicalDatastoreType.CONFIGURATION, id);
        doReturn(Futures.immediateFuture(null)).when(facade).patchConfig(eq(id), any(NormalizedNode.class));
        dataBroker = new RestconfDataBroker(facade);
    }

    @Test
    public void testNewReadOnlyTransaction() throws Exception {
        dataBroker.newReadOnlyTransaction().read(LogicalDatastoreType.CONFIGURATION, id);
        verify(facade).getData(LogicalDatastoreType.CONFIGURATION, id);
    }

    @Test
    public void testNewReadWriteTransaction() throws Exception {
        final DOMDataReadWriteTransaction readWriteTransaction = dataBroker.newReadWriteTransaction();
        readWriteTransaction.read(LogicalDatastoreType.CONFIGURATION, id);
        final NormalizedNode<?, ?> data = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(id.getLastPathArgument().getNodeType()))
                .build();
        readWriteTransaction.merge(LogicalDatastoreType.CONFIGURATION, id, data);
        readWriteTransaction.submit();
        verify(facade).getData(LogicalDatastoreType.CONFIGURATION, id);
        verify(facade).patchConfig(id, data);
    }

    @Test
    public void testNewWriteOnlyTransaction() throws Exception {
        final DOMDataWriteTransaction readWriteTransaction = dataBroker.newWriteOnlyTransaction();
        final NormalizedNode<?, ?> data = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(id.getLastPathArgument().getNodeType()))
                .build();
        readWriteTransaction.merge(LogicalDatastoreType.CONFIGURATION, id, data);
        readWriteTransaction.submit();
        verify(facade).patchConfig(id, data);
    }

    @Test
    public void testGetSupportedExtensions() throws Exception {
        Assert.assertTrue(dataBroker.getSupportedExtensions().isEmpty());
    }
}