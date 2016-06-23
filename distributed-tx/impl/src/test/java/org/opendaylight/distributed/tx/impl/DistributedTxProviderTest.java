/*
 * Copyright and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.spi.TxException;
import org.opendaylight.distributed.tx.spi.TxProvider;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DistributedTxProviderTest {
    private TxProvider txProvider;
    private DTxProviderImpl dTxProvider;
    private ExecutorService threadPool;
    private volatile int exceptionOccurNum = 0;

    InstanceIdentifier<TestClassNode1> node1 = InstanceIdentifier.create(TestClassNode1.class);
    InstanceIdentifier<TestClassNode2> node2 = InstanceIdentifier.create(TestClassNode2.class);
    InstanceIdentifier<TestClassNode3> node3 = InstanceIdentifier.create(TestClassNode3.class);
    InstanceIdentifier<TestClassNode4> node4 = InstanceIdentifier.create(TestClassNode4.class);
    InstanceIdentifier<TestClassNode5> node5 = InstanceIdentifier.create(TestClassNode5.class);
    Map<DTXLogicalTXProviderType, TxProvider> m = new HashMap<>();

    private class myTxProvider implements TxProvider{
        private final Set<InstanceIdentifier<?>> nodeSet = new HashSet<>();

        @Override
        public ReadWriteTransaction newTx(InstanceIdentifier<?> nodeId) throws TxException.TxInitiatizationFailedException {
            return new DTXTestTransaction();
        }

        @Override
        public boolean isDeviceLocked(InstanceIdentifier<?> device) {
            boolean lock = false;
            synchronized (this){
                if (nodeSet.contains(device))
                    lock = true;
            }
            return lock;
        }

        @Override
        public boolean lockTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
            boolean ret = true;

            synchronized (this) {
                Set<InstanceIdentifier<?>> s = new HashSet<>();
                s.addAll(nodeSet);

                s.retainAll(deviceSet);

                if(s.size() > 0)
                    ret = false;
                else {
                    nodeSet.addAll(deviceSet);
                }
            }

            return ret;
        }

        @Override
        public void releaseTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
            synchronized (this) {
                nodeSet.removeAll(deviceSet);
            }
        }
    }

    private class TestClassNode1  implements DataObject {
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }
    private class TestClassNode2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestClassNode3 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestClassNode4 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestClassNode5 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    //Task1 gets DTx from the dtxProvider for node1, node2 and node3
    private class Task1 implements Runnable{

        @Override
        public void run() {
            Set<InstanceIdentifier<?>> s1 = Sets.newSet(node1, node2, node3);
            try
            {
                dTxProvider.newTx(s1);
            }catch (Exception e)
            {
                exceptionOccurNum++;
                Assert.assertTrue("Get wrong exception", e instanceof DTxException.DTxInitializationFailedException);
            }
        }
    }
   //Task2 gets DTx from the dtxProvider for node3, node4, node5
    private class Task2 implements Runnable{

        @Override
        public void run() {
            Set<InstanceIdentifier<?>> s2 = Sets.newSet(node3, node4, node5);
            try{
                dTxProvider.newTx(s2);
            }catch (Exception e)
            {
                exceptionOccurNum++;
                Assert.assertTrue("Get wrong exception", e instanceof DTxException.DTxInitializationFailedException);
            }
        }
    }

    /**
     * Initiate DTxProvider
     */
    @Before
    public void testOnSessionInitiated() {
        txProvider = new myTxProvider();
        m.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, txProvider);
        dTxProvider = new DTxProviderImpl(m);
    }

    /**
     * Test concurrency of newTx().
     */
    @Test
    public void testNewTx() {
        int expectedExceptionOccurNum = 1;
        threadPool = Executors.newFixedThreadPool(2);
          threadPool.execute(new Task1());
          threadPool.execute(new Task2());
          threadPool.shutdown();
          while(!threadPool.isTerminated()) {
              Thread.yield();
          }
          Assert.assertEquals("Should only get one exception", expectedExceptionOccurNum, exceptionOccurNum);
    }

    @Test
    public void testClose() throws Exception {
        m.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, txProvider);
        DTxProviderImpl provider = new DTxProviderImpl(m);
        // Ensure no exceptions
        provider.close();
    }
}
