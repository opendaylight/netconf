/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.fail;

public class RollbackImplTest {
    InstanceIdentifier<TestData1> identifier1 = InstanceIdentifier.create(TestData1.class);
    InstanceIdentifier<TestData2> identifier2 = InstanceIdentifier.create(TestData2.class);
    InstanceIdentifier<TestNode1> node1 = InstanceIdentifier.create(TestNode1.class);
    InstanceIdentifier<TestNode2> node2 = InstanceIdentifier.create(TestNode2.class);

    private class TestData1 implements DataObject {
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestData2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestNode1 implements DataObject {
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestNode2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    /**
     * Test successful rollback().
     */
    @Test
    public void testRollBack() {
        int  expectedDataNumInNodeIdentifier = 0;
        final DTXTestTransaction testTransaction1 = new DTXTestTransaction();
        final DTXTestTransaction testTransaction2 = new DTXTestTransaction();
        final CachingReadWriteTx cachingReadWriteTx1 = new CachingReadWriteTx(testTransaction1);
        final CachingReadWriteTx cachingReadWriteTx2 = new CachingReadWriteTx(testTransaction2);
        Set<InstanceIdentifier<?>> s = Sets.newHashSet(node1, node2);
        Map<InstanceIdentifier<?>, ? extends CachingReadWriteTx> perNodeCaches;
        Map<InstanceIdentifier<?>, ReadWriteTransaction> perNodeRollbackTxs;

        testTransaction1.addInstanceIdentifiers(identifier1, identifier2);
        testTransaction2.addInstanceIdentifiers(identifier1, identifier2);

        CheckedFuture<Void, DTxException> writeFuture1 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> writeFuture2 = cachingReadWriteTx1.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());
        CheckedFuture<Void, DTxException> writeFuture3 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        CheckedFuture<Void, DTxException> writeFuture4 = cachingReadWriteTx2.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier2, new TestData2());

        try {
            writeFuture1.checkedGet();
            writeFuture2.checkedGet();
            writeFuture3.checkedGet();
            writeFuture4.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        perNodeCaches = Maps.toMap(s, new Function<InstanceIdentifier<?>, CachingReadWriteTx>() {
            @Nullable
            @Override
            public CachingReadWriteTx apply(@Nullable InstanceIdentifier<?> instanceIdentifier) {
                return instanceIdentifier == node1? cachingReadWriteTx1:cachingReadWriteTx2;
            }
        });

        perNodeRollbackTxs = Maps.toMap(s, new Function<InstanceIdentifier<?>, ReadWriteTransaction>() {
            @Nullable
            @Override
            public ReadWriteTransaction apply(@Nullable InstanceIdentifier<?> instanceIdentifier) {
                return instanceIdentifier == node1 ? testTransaction1 : testTransaction2;
            }
        });

        RollbackImpl testRollBack = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollBackFut = testRollBack.rollback(perNodeCaches, perNodeRollbackTxs);

        try {
           rollBackFut.checkedGet();
        }catch (Exception e) {
           fail("Get rollback exception");
        }

        Assert.assertEquals("Data size in tx1 is wrong", expectedDataNumInNodeIdentifier,testTransaction1.getTxDataSizeByIid(identifier1));
        Assert.assertEquals("Data size in tx1 is wrong", expectedDataNumInNodeIdentifier,testTransaction1.getTxDataSizeByIid(identifier2));
        Assert.assertEquals("Data size in tx2 is wrong", expectedDataNumInNodeIdentifier,testTransaction2.getTxDataSizeByIid(identifier1));
        Assert.assertEquals("Data size in tx2 is wrong", expectedDataNumInNodeIdentifier,testTransaction2.getTxDataSizeByIid(identifier2));
    }

     /**
      * Test rollback() with write exception.
      */
    @Test
    public void testRollbackFailWithWriteException() {
        DTXTestTransaction testTransaction = new DTXTestTransaction();
        testTransaction.addInstanceIdentifiers(identifier1);
        CachingReadWriteTx cachingReadWriteTx = new CachingReadWriteTx(testTransaction);

        CheckedFuture<Void, DTxException> writeFuture = cachingReadWriteTx.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1,new TestData1());
        try {
            writeFuture.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        Map<InstanceIdentifier<?>, CachingReadWriteTx> perNodeCaches = Maps.newHashMap();
        perNodeCaches.put(node1, cachingReadWriteTx);

        Map<InstanceIdentifier<?>, ReadWriteTransaction> perNodeRollbackTxs = Maps.newHashMap();
        perNodeRollbackTxs.put(node1, testTransaction);

        testTransaction.setDeleteExceptionByIid(identifier1,true);
        RollbackImpl testRollback = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture =  testRollback.rollback(perNodeCaches,perNodeRollbackTxs);

        try{
            rollbackFuture.checkedGet();
            fail("Can't get rollback exception");
        }catch (Exception e) {
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test rollback() with submit exception
     */
    @Test
    public void testRollbackFailWithSubmitException() {
        DTXTestTransaction testTransaction = new DTXTestTransaction();
        testTransaction.addInstanceIdentifiers(identifier1);
        CachingReadWriteTx cachingReadWriteTx = new CachingReadWriteTx(testTransaction);
        Map<InstanceIdentifier<?>, CachingReadWriteTx> perNodeCaches = Maps.newHashMap();
        Map<InstanceIdentifier<?>, ReadWriteTransaction> perNodeRollbackTxs = Maps.newHashMap();

        CheckedFuture<Void, DTxException> writeFuture = cachingReadWriteTx.asyncPut(LogicalDatastoreType.OPERATIONAL, identifier1, new TestData1());
        try {
            writeFuture.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        perNodeCaches.put(node1, cachingReadWriteTx);
        perNodeRollbackTxs.put(node1, testTransaction);
        testTransaction.setSubmitException(true);
        RollbackImpl testRollback = new RollbackImpl();
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture =  testRollback.rollback(perNodeCaches,perNodeRollbackTxs);

        try{
            rollbackFuture.checkedGet();
            fail("Can't get the rollback exception");
        }catch (Exception e) {
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }
}
