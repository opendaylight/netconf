/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class ReadDataTransactionUtilTest {

    private static final TestData data = new TestData();
    private static final YangInstanceIdentifier.NodeIdentifier nodeIdentifier = new YangInstanceIdentifier
            .NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    private TransactionVarsWrapper wrapper;
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private InstanceIdentifierContext<?> context;
    @Mock
    private DOMDataReadOnlyTransaction read;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(read).when(transactionChain).newReadOnlyTransaction();
        wrapper = new TransactionVarsWrapper(this.context, null, this.transactionChain);
    }

    @Test
    public void readDataConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(data.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(data.data3, normalizedNode);//
    }

    @Test
    public void readAllHavingOnlyConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path);
        doReturn(data.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(data.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyNonConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path2);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path2);
        doReturn(data.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(data.data2, normalizedNode);
    }

    @Test
    public void readDataNonConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path2);
        doReturn(data.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.NONCONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(data.data2, normalizedNode);
    }

    @Test
    public void readContainerDataAllTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path);
        doReturn(data.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(nodeIdentifier)
                .withChild(data.contentLeaf)
                .withChild(data.contentLeaf2)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readContainerDataConfigNoValueOfContentTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path);
        doReturn(data.path).when(context).getInstanceIdentifier();
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(null, wrapper);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(nodeIdentifier)
                .withChild(data.contentLeaf)
                .withChild(data.contentLeaf2)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readListDataAllTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.listData))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, data.path3);
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.listData2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path3);
        doReturn(data.path3).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        final MapNode checkingData = Builders
                .mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
                .withChild(data.checkData)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void readDataWrongPathOrNoContentTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, data.path2);
        doReturn(data.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertNull(normalizedNode);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void readDataFailTest() {
        final String valueOfContent = RestconfDataServiceConstant.ReadData.READ_TYPE_TX;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, null);
        assertNull(normalizedNode);
    }
}
