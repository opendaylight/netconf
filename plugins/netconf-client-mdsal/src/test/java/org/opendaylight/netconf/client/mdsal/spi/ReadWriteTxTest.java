/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.FluentFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

@ExtendWith(MockitoExtension.class)
class ReadWriteTxTest {
    @Mock
    private DOMDataTreeReadTransaction delegateReadTx;
    @Mock
    private DOMDataTreeWriteTransaction delegateWriteTx;
    private ReadWriteTx<?> tx;

    @BeforeEach
    void setUp() throws Exception {
        tx = new ReadWriteTx<>(delegateReadTx, delegateWriteTx);
    }

    @Test
    void submit() {
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
    void cancel() {
        tx.cancel();
        verify(delegateWriteTx).cancel();
    }

    @Test
    void read() throws Exception {
        tx.read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId());
        verify(delegateReadTx).read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId());
    }

    @Test
    void exists() throws Exception {
        final YangInstanceIdentifier id = TxTestUtils.getContainerId();
        when(delegateReadTx.exists(LogicalDatastoreType.CONFIGURATION, id)).thenReturn(
            FluentFutures.immediateTrueFluentFuture());
        final FluentFuture<Boolean> exists = tx.exists(LogicalDatastoreType.CONFIGURATION, id);
        assertTrue(exists.get());
    }

    @Test
    void getIdentifier() {
        final ReadWriteTx<?> tx2 = new ReadWriteTx<>(delegateReadTx, delegateWriteTx);
        assertNotEquals(tx.getIdentifier(), tx2.getIdentifier());
    }
}
