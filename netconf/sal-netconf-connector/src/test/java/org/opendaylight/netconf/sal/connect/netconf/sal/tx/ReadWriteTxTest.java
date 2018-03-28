/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

public class ReadWriteTxTest {
    @Mock
    private DOMDataReadTransaction delegateReadTx;
    @Mock
    private DOMDataWriteTransaction delegateWriteTx;
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
        tx.submit();
        verify(delegateWriteTx).submit();
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

    @Test
    public void exists() throws Exception {
        final YangInstanceIdentifier id = TxTestUtils.getContainerId();
        final CheckedFuture<Boolean, ReadFailedException> resultFuture =
                Futures.immediateCheckedFuture(true);
        when(delegateReadTx.exists(LogicalDatastoreType.CONFIGURATION, id)).thenReturn(resultFuture);
        final CheckedFuture<Boolean, ReadFailedException> exists = tx.exists(LogicalDatastoreType.CONFIGURATION, id);
        Assert.assertTrue(exists.get());
    }

    @Test
    public void getIdentifier() throws Exception {
        final ReadWriteTx tx2 = new ReadWriteTx(null, null);
        Assert.assertNotEquals(tx.getIdentifier(), tx2.getIdentifier());
    }

}
