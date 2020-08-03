/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ReadWriteTxTest {
    @Mock
    private DOMDataTreeReadTransaction delegateReadTx;
    @Mock
    private DOMDataTreeWriteTransaction delegateWriteTx;
    @Mock
    private RpcError rpcError;
    private ReadWriteTx tx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        tx = new ReadWriteTx(delegateReadTx, delegateWriteTx);
    }

    @Test
    public void submit() throws Exception {
        final YangInstanceIdentifier id1 = TxTestUtils.getContainerId();
        final ContainerNode containerNode = TxTestUtils.getContainerNode();
        tx.put(LogicalDatastoreType.CONFIGURATION, id1, containerNode);
        verify(delegateWriteTx).put(LogicalDatastoreType.CONFIGURATION, id1, containerNode);
        final YangInstanceIdentifier id2 = TxTestUtils.getLeafId();
        final LeafNode<String> leafNode = TxTestUtils.getLeafNode();
        tx.merge(LogicalDatastoreType.CONFIGURATION, id2, leafNode);
        verify(delegateWriteTx).merge(LogicalDatastoreType.CONFIGURATION, id2, leafNode);
        tx.delete(LogicalDatastoreType.CONFIGURATION, id2);
        verify(delegateWriteTx).delete(LogicalDatastoreType.CONFIGURATION, id2);
        tx.commit();
        verify(delegateWriteTx).commit();
    }

    @Test
    public void cancel() throws Exception {
        tx.cancel();
        verify(delegateWriteTx).cancel();
    }

    @Test
    public void read() throws Exception {
        tx.read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId());
        verify(delegateReadTx).read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId());
    }

    @Test(expected = ExecutionException.class)
    public void readNegativeTest() throws Exception {
        ReadWriteTx readWriteTx = Mockito.spy(tx);
        Mockito.doReturn(rpcError).when(readWriteTx).getResultsFutures();
        when(rpcError.getTag()).thenReturn(XmlNetconfConstants.LOCK_DENIED);
        final FluentFuture<Optional<NormalizedNode<?, ?>>> read =
            readWriteTx.read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId());
        read.get();
    }

    @Test
    public void exists() throws Exception {
        final YangInstanceIdentifier id = TxTestUtils.getContainerId();
        when(delegateReadTx.exists(LogicalDatastoreType.CONFIGURATION, id)).thenReturn(
            FluentFutures.immediateTrueFluentFuture());
        final FluentFuture<Boolean> exists = tx.exists(LogicalDatastoreType.CONFIGURATION, id);
        assertTrue(exists.get());
    }

    @Test(expected = ExecutionException.class)
    public void existsNegativeTest() throws Exception {
        final YangInstanceIdentifier id = TxTestUtils.getContainerId();
        ReadWriteTx readWriteTx = Mockito.spy(tx);
        Mockito.doReturn(rpcError).when(readWriteTx).getResultsFutures();
        when(rpcError.getTag()).thenReturn(XmlNetconfConstants.LOCK_DENIED);
        final FluentFuture<Boolean> exists = readWriteTx.exists(LogicalDatastoreType.CONFIGURATION, id);
        exists.get();
    }

    @Test
    public void getIdentifier() throws Exception {
        final ReadWriteTx tx2 = new ReadWriteTx(null, null);
        assertNotEquals(tx.getIdentifier(), tx2.getIdentifier());
    }
}
