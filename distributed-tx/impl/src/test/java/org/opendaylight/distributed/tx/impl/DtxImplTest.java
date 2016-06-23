/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.spi.TxException;
import org.opendaylight.distributed.tx.spi.TxProvider;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.Assert.fail;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;

public class DtxImplTest{
    InstanceIdentifier<NetconfNode1> netConfNodeId1;
    InstanceIdentifier<NetConfNode2> netConfNodeId2;
    InstanceIdentifier<DataStoreNode1> dataStoreNodeId1;
    InstanceIdentifier<DataStoreNode2> dataStoreNodeId2;

    InstanceIdentifier<TestIid1> iid1;
    InstanceIdentifier<TestIid2> iid2;
    InstanceIdentifier<TestIid3> iid3;
    InstanceIdentifier<TestIid4> iid4;

    DTXTestTransaction internalDtxNetconfTestTx1;
    DTXTestTransaction internalDtxNetconfTestTx2;
    DTXTestTransaction internalDtxDataStoreTestTx1;
    DTXTestTransaction internalDtxDataStoreTestTx2;

    Set<InstanceIdentifier<?>> netconfNodes;
    Set<InstanceIdentifier<?>> dataStoreNodes;
    Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap;
    List<InstanceIdentifier<? extends TestIid>> identifiers;
    TestClass testClass;
    DtxImpl netConfOnlyDTx;
    DtxImpl mixedDTx;
    ExecutorService threadPool;

    private class myNetconfTxProvider implements TxProvider{
        @Override
        public ReadWriteTransaction newTx(InstanceIdentifier<?> nodeId) throws TxException.TxInitiatizationFailedException {
            return nodeId == netConfNodeId1 ? internalDtxNetconfTestTx1 : internalDtxNetconfTestTx2;
        }

        @Override
        public boolean isDeviceLocked(InstanceIdentifier<?> device) {
            return false;
        }

        @Override
        public boolean lockTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
            return true;
        }

        @Override
        public void releaseTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {

        }
    }

    private class myDataStoreTxProvider implements TxProvider{
        @Override
        public ReadWriteTransaction newTx(InstanceIdentifier<?> nodeId) throws TxException.TxInitiatizationFailedException {
            return nodeId == dataStoreNodeId1 ? internalDtxDataStoreTestTx1 : internalDtxDataStoreTestTx2;
        }

        @Override
        public boolean isDeviceLocked(InstanceIdentifier<?> device) {
            return false;
        }

        @Override
        public boolean lockTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
            return true;
        }

        @Override
        public void releaseTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {

        }
    }

    private class TestIid implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }
    private class TestIid1 extends TestIid implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestIid2 extends TestIid implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class TestIid3 extends TestIid implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }
    private class TestIid4 extends TestIid implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class NetconfNode1 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class NetConfNode2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class DataStoreNode1 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private class DataStoreNode2 implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return null;
        }
    }

    private enum ProviderType{
        NETCONF, MIX
    }

    private enum OperationType{
        READ, PUT, MERGE, DELETE
    }

    private class TestClass{
        /**
         * Test successful writeAndRollbackOnFailure()
         * @param providerType transaction provider type
         * @param operationType operation type
         * @return writing CheckedFuture list
         */
        List<CheckedFuture<Void, DTxException>> testWriteAndRollbackOnFailure(ProviderType providerType, OperationType operationType){
            List<CheckedFuture<Void, DTxException>> writeFutures = new ArrayList<>();
            if (operationType == OperationType.DELETE){
                internalDtxNetconfTestTx1.createObjForIdentifier(iid1);
                internalDtxNetconfTestTx2.createObjForIdentifier(iid1);
                internalDtxDataStoreTestTx1.createObjForIdentifier(iid1);
                internalDtxDataStoreTestTx2.createObjForIdentifier(iid1);
            }
            if (providerType == ProviderType.NETCONF){
                CheckedFuture<Void, DTxException> netConfFuture1 = writeData(
                        netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType, iid1, netConfNodeId1, new TestIid1());
                CheckedFuture<Void, DTxException> netConfFuture2 = writeData(
                        netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType, iid1, netConfNodeId2, new TestIid1());
                writeFutures.add(netConfFuture1);
                writeFutures.add(netConfFuture2);
            }else {
                CheckedFuture<Void, DTxException> netConfFuture1 = writeData(
                        mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType, iid1, netConfNodeId1, new TestIid1());
                CheckedFuture<Void, DTxException> netConfFuture2 = writeData(
                        mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType, iid1, netConfNodeId2, new TestIid1());
                CheckedFuture<Void, DTxException> dataStoreFuture1 = writeData(
                        mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, operationType, iid1, dataStoreNodeId1, new TestIid1());
                CheckedFuture<Void, DTxException> dataStoreFuture2 = writeData(
                        mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, operationType, iid1, dataStoreNodeId2, new TestIid1());
                writeFutures.add(netConfFuture1);
                writeFutures.add(netConfFuture2);
                writeFutures.add(dataStoreFuture1);
                writeFutures.add(dataStoreFuture2);
            }
            return writeFutures;
        }

        /**
         * Test writeAndRollbackOnFailure() with failed write and successful rollback
         * @param providerType transaction providers type
         * @param operationType operation type
         * @param errorType operation type with exception
         * @return CheckedFuture indicating the result of error transaction
         */
        CheckedFuture<Void, DTxException> testWriteAndRollbackOnFailureRollbackSucceed(ProviderType providerType, OperationType operationType,
                                                          OperationType errorType){
            CheckedFuture<Void, DTxException> writeFuture = null;
            if (operationType == OperationType.DELETE){
                internalDtxNetconfTestTx1.createObjForIdentifier(iid1);
                internalDtxNetconfTestTx2.createObjForIdentifier(iid1);
                internalDtxDataStoreTestTx1.createObjForIdentifier(iid1);
                internalDtxDataStoreTestTx2.createObjForIdentifier(iid1);
            }
            if (providerType == ProviderType.NETCONF){
                CheckedFuture<Void, DTxException> netConfFuture1 =  writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        operationType, iid1, netConfNodeId1, new TestIid1());
                try {
                    netConfFuture1.checkedGet();
                }catch (Exception e) {
                    fail("Caught unexpected exception");
                }

                setException(internalDtxNetconfTestTx2, iid1, errorType);
                writeFuture= writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        operationType, iid1, netConfNodeId2, new TestIid1());
            }else{
                CheckedFuture<Void, DTxException> netConfFuture1 = writeData(mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        operationType, iid1, netConfNodeId1,new TestIid1());
                CheckedFuture<Void, DTxException> netConfFuture2 = writeData(mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        operationType, iid1, netConfNodeId2,new TestIid1());
                CheckedFuture<Void, DTxException> dataStoreFuture1 = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                        operationType, iid1, dataStoreNodeId1,new TestIid1());
                try{
                    netConfFuture1.checkedGet();
                    netConfFuture2.checkedGet();
                    dataStoreFuture1.checkedGet();
                }catch (Exception e) {
                    fail("Caught unexpected exception");
                }

                setException(internalDtxDataStoreTestTx2, iid1, operationType);
                writeFuture = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                        operationType, iid1, dataStoreNodeId2,new TestIid1());
            }
            return writeFuture;
        }

        /**
         * Test writeAndRollbackOnFailure() with failed rollback
         * @param providerType transaction providers type
         * @param operationType operation type
         * @return CheckedFuture indicating the result of error transaction
         */
        CheckedFuture<Void, DTxException> testWriteAndRollbackOnFailureRollbackFail(ProviderType providerType, OperationType operationType){
            CheckedFuture<Void, DTxException> writeFuture = null;
            if (providerType == ProviderType.NETCONF){
                setException(internalDtxNetconfTestTx2, iid1, OperationType.PUT);
                internalDtxNetconfTestTx2.setSubmitException(true);
                writeFuture = writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        operationType, iid1, netConfNodeId2,new TestIid1());

            }else{
                setException(internalDtxDataStoreTestTx2, iid1, OperationType.PUT);
                internalDtxDataStoreTestTx2.setSubmitException(true);
                writeFuture = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                        operationType, iid1, dataStoreNodeId2,new TestIid1());
            }
            return writeFuture;
        }

        /**
         * Test concurrency of writeAndRollbackOnFailure() 
         * @param providerType transaction providers type
         * @param operationType operation type
         */
        void testConcurrentWriteAndRollbackOnFailure(ProviderType providerType, final OperationType operationType, int numOfThreads){
            threadPool = Executors.newFixedThreadPool(numOfThreads);
            if (operationType == OperationType.DELETE){
                for (int i = 0; i < numOfThreads; i++) {
                    internalDtxNetconfTestTx1.createObjForIdentifier(identifiers.get(i));
                    internalDtxDataStoreTestTx1.createObjForIdentifier(identifiers.get(i));
                }
            }
            if (providerType == ProviderType.NETCONF){
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            CheckedFuture<Void, DTxException> writeFuture = writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), netConfNodeId1,new TestIid());
                            try{
                                writeFuture.checkedGet();
                            }catch (Exception e) {
                                fail("Caught unexpected exception");
                            }
                        }
                    });
                }

            }else {
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            CheckedFuture<Void, DTxException> netConFuture = writeData(mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), netConfNodeId1,new TestIid());
                            CheckedFuture<Void, DTxException> dataStoreFuture = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), dataStoreNodeId1,new TestIid());
                            try{
                                netConFuture.checkedGet();
                                dataStoreFuture.checkedGet();
                            }catch (Exception e) {
                                fail("Caught unexpected exception");
                            }
                        }
                    });
                }
            }
            threadPool.shutdown();
            while (!threadPool.isTerminated()) {
                Thread.yield();
            }
        }

        /**
         * Test concurrency of writeAndRollbackOnFailure() with failed write and successful rollback for different iids
         * @param providerType transaction providers type
         * @param operationType operation type
         * @param errorType operation type with exception
         * @param numOfThreads number of of testing threads
         */
        void testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType providerType, final OperationType operationType,
                                                                    final OperationType errorType, int numOfThreads){
            threadPool = Executors.newFixedThreadPool(numOfThreads);
            final int errorOccur = (int)(Math.random() * numOfThreads);
            if (operationType == OperationType.DELETE){
                for (int i = 0; i < numOfThreads; i++) {
                    internalDtxNetconfTestTx1.createObjForIdentifier(identifiers.get(i));
                    internalDtxDataStoreTestTx1.createObjForIdentifier(identifiers.get(i));
                }
            }
            if (providerType == ProviderType.NETCONF){
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (finalI == errorOccur){
                                setException(internalDtxNetconfTestTx1, identifiers.get(finalI), errorType);
                            }
                            CheckedFuture<Void, DTxException> f = writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), netConfNodeId1, new TestIid());
                            try{
                                f.checkedGet();
                            }catch (Exception e) {
                                if (finalI != errorOccur)
                                    fail("Caught unexpected exception");
                                else
                                    Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
                            }
                        }
                    });
                }
            }else{
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (finalI == errorOccur){
                                setException(internalDtxNetconfTestTx1, identifiers.get(finalI), errorType);
                            }
                            CheckedFuture<Void, DTxException> netConfFuture = writeData(mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), netConfNodeId1, new TestIid());
                            CheckedFuture<Void, DTxException> dataStoreFuture = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), dataStoreNodeId1, new TestIid());
                            try{
                                netConfFuture.checkedGet();
                                dataStoreFuture.checkedGet();
                            }catch (Exception e) {
                                if (finalI != errorOccur)
                                    fail("Caught unexpected exception");
                                else
                                    Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
                            }
                        }
                    });
                }
            }
            threadPool.shutdown();
            while (!threadPool.isTerminated()) {
                Thread.yield();
            }
        }

        /**
         * Test concurrency of writeAndRollbackOnFailure() with failed write and successful rollback for the same iid
         * @param providerType transaction providers type
         * @param operationType operation type
         * @param errorType operation type with exception
         * @param numOfThreads number of testing threads
         */
        void testConcurrentWriteToSameIidRollbackSucceed(ProviderType providerType, final OperationType operationType,
                                                         final OperationType errorType, int numOfThreads){
            threadPool = Executors.newFixedThreadPool(numOfThreads);
            final int errorOccur = (int)(Math.random() * numOfThreads);
            if (operationType == OperationType.DELETE){
                internalDtxNetconfTestTx1.createObjForIdentifier(iid1);
                internalDtxDataStoreTestTx1.createObjForIdentifier(iid1);
            }
            if (providerType == ProviderType.NETCONF){
                for (int i = 0; i < numOfThreads; i++){
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (finalI == errorOccur){
                                setException(internalDtxNetconfTestTx1, iid1, errorType);
                            }
                            CheckedFuture<Void, DTxException> writeFuture = writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                                    operationType, iid1, netConfNodeId1, new TestIid1());
                            try{
                                writeFuture.checkedGet();
                            }catch (DTxException e){
                                if (finalI != errorOccur)
                                    fail("Caught unexpected exception");
                                else
                                    Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
                            }
                        }
                    });
                }
            }else{
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (finalI == errorOccur) {
                                setException(internalDtxDataStoreTestTx1, iid1, errorType);
                            }
                            CheckedFuture<Void, DTxException> dataStoreWriteFuture = writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                                    operationType, (InstanceIdentifier<TestIid>)identifiers.get(finalI), dataStoreNodeId1, new TestIid());
                            try {
                                dataStoreWriteFuture.checkedGet();
                            } catch (DTxException e) {
                                if (finalI != errorOccur)
                                    fail("Caught unexpected exception");
                                else
                                    Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
                            }
                        }
                    });
                }
            }
            threadPool.shutdown();
            while(!threadPool.isTerminated()){
                Thread.yield();
            }
        }

        /**
         * Test submit() with multiple threads to successfully write data to nodes
         * @param providerType transaction providers type
         * @param operationType operation type
         * @param numOfThreads number of testing threads
         */
        void testConcurrentWriteAndSubmit(ProviderType providerType, final OperationType operationType, int numOfThreads){
            threadPool = Executors.newFixedThreadPool(numOfThreads * netconfNodes.size());
            if (operationType == OperationType.DELETE){
                for (int i = 0; i < numOfThreads; i++){
                    internalDtxNetconfTestTx1.createObjForIdentifier(identifiers.get(i));
                    internalDtxNetconfTestTx2.createObjForIdentifier(identifiers.get(i));
                    internalDtxDataStoreTestTx1.createObjForIdentifier(identifiers.get(i));
                    internalDtxDataStoreTestTx2.createObjForIdentifier(identifiers.get(i));
                }
            }
            if (providerType == ProviderType.NETCONF) {
                for (final InstanceIdentifier<?> nodeIid : netconfNodes) {
                    for (int i = 0; i < numOfThreads; i++) {
                        final int finalI = i;
                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType,
                                        (InstanceIdentifier<TestIid>) identifiers.get(finalI), nodeIid, new TestIid1());
                            }
                        });
                    }
                }
            }else{
                for (final DTXLogicalTXProviderType type : nodesMap.keySet()){
                    for (final InstanceIdentifier<?> nodeIid : nodesMap.get(type)){
                        for (int i = 0; i < numOfThreads; i++) {
                            final int finalI = i;
                            threadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    writeData(mixedDTx, type, operationType,
                                            (InstanceIdentifier<TestIid>) identifiers.get(finalI), nodeIid, new TestIid1());
                                }
                            });
                        }

                    }
                }
            }
            threadPool.shutdown();
            while (!threadPool.isTerminated()){
                Thread.yield();
            }
        }

        /**
         * Test rollback(). DTx wait for all writing actions finish before performing rollback
         * @param providerType transaction providers type
         * @param operationType operation type
         * @param numOfThreads number of testing threads
         * @return CheckedFuture indicating the result of rollback
         */
        CheckedFuture<Void, DTxException.RollbackFailedException> testConcurrentWriteAndRollback(ProviderType providerType, final OperationType operationType, int numOfThreads){
            threadPool = Executors.newFixedThreadPool(numOfThreads);
            CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = null;
            if (operationType == OperationType.DELETE){
                for (int i = 0; i < numOfThreads; i++){
                    internalDtxNetconfTestTx1.createObjForIdentifier(identifiers.get(i));
                    internalDtxDataStoreTestTx1.createObjForIdentifier(identifiers.get(i));
                }
            }
            if (providerType == ProviderType.NETCONF) {
                for (int i = 0; i < numOfThreads; i++) {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            writeData(netConfOnlyDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType,
                                    (InstanceIdentifier<TestIid>) identifiers.get(finalI), netConfNodeId1, new TestIid1());
                        }
                    });
                }
                threadPool.shutdown();
                while (!threadPool.isTerminated()) {
                    Thread.yield();
                }
                rollbackFuture = netConfOnlyDTx.rollback();
            }else{
                for (int i = 0; i < numOfThreads; i++)
                {
                    final int finalI = i;
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            writeData(mixedDTx, DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, operationType,
                                    (InstanceIdentifier<TestIid>) identifiers.get(finalI), netConfNodeId1, new TestIid1());
                            writeData(mixedDTx, DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, operationType,
                                    (InstanceIdentifier<TestIid>) identifiers.get(finalI), dataStoreNodeId1, new TestIid1());
                        }
                    });
                }
                threadPool.shutdown();
                while (!threadPool.isTerminated()) {
                    Thread.yield();
                }
                rollbackFuture = mixedDTx.rollback();
            }
            return rollbackFuture;
        }

        private <T extends DataObject> CheckedFuture<Void, DTxException> writeData(DTx dTx, DTXLogicalTXProviderType providerType, OperationType operationType,
                                                                                   InstanceIdentifier<T> iid, InstanceIdentifier<?> nodeId, T data){
            CheckedFuture<Void, DTxException> writeFuture = null;
            switch (operationType){
                case PUT:
                    writeFuture = dTx.putAndRollbackOnFailure(providerType, LogicalDatastoreType.OPERATIONAL, iid, data, nodeId);
                    break;
                case MERGE:
                    writeFuture = dTx.mergeAndRollbackOnFailure(providerType, LogicalDatastoreType.OPERATIONAL, iid, data, nodeId);
                    break;
                case DELETE:
                    writeFuture = dTx.deleteAndRollbackOnFailure(providerType, LogicalDatastoreType.OPERATIONAL, iid, nodeId);
            }
            return writeFuture;
        }

        private void setException(DTXTestTransaction testTx, InstanceIdentifier<?> iid, OperationType type){
            if (type == OperationType.READ){
                testTx.setReadExceptionByIid(iid, true);
            }else if (type == OperationType.PUT){
                testTx.setPutExceptionByIid(iid, true);
            }else if (type == OperationType.MERGE){
                testTx.setMergeExceptionByIid(iid, true);
            }else{
                testTx.setDeleteExceptionByIid(iid, true);
            }
        }
    }

    @Before
    @Test
    public void testInit(){
        netconfNodes = new HashSet<>();
        this.netConfNodeId1 = InstanceIdentifier.create(NetconfNode1.class);
        netconfNodes.add(netConfNodeId1);
        this.netConfNodeId2 = InstanceIdentifier.create(NetConfNode2.class);
        netconfNodes.add(netConfNodeId2);
        iid1 = InstanceIdentifier.create(TestIid1.class);
        iid2 = InstanceIdentifier.create(TestIid2.class);
        iid3 = InstanceIdentifier.create(TestIid3.class);
        iid4 = InstanceIdentifier.create(TestIid4.class);
        identifiers = Lists.newArrayList(iid1, iid2, iid3, iid4);

        internalDtxNetconfTestTx1 = new DTXTestTransaction();
        internalDtxNetconfTestTx1.addInstanceIdentifiers(iid1, iid2, iid3, iid4);
        internalDtxNetconfTestTx2 = new DTXTestTransaction();
        internalDtxNetconfTestTx2.addInstanceIdentifiers(iid1, iid2, iid3, iid4);
        Map<DTXLogicalTXProviderType, TxProvider> netconfTxProviderMap = new HashMap<>();
        TxProvider netconfTxProvider = new myNetconfTxProvider();
        netconfTxProviderMap.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, netconfTxProvider);
        netConfOnlyDTx = new DtxImpl(netconfTxProvider, netconfNodes, new DTxTransactionLockImpl(netconfTxProviderMap));

        testClass = new TestClass();

        dataStoreNodes = new HashSet<>();
        this.dataStoreNodeId1 = InstanceIdentifier.create(DataStoreNode1.class);
        dataStoreNodes.add(dataStoreNodeId1);
        this.dataStoreNodeId2 = InstanceIdentifier.create(DataStoreNode2.class);
        dataStoreNodes.add(dataStoreNodeId2);
        internalDtxDataStoreTestTx1 = new DTXTestTransaction();
        internalDtxDataStoreTestTx1.addInstanceIdentifiers(iid1, iid2, iid3, iid4);
        internalDtxDataStoreTestTx2 = new DTXTestTransaction();
        internalDtxDataStoreTestTx2.addInstanceIdentifiers(iid1, iid2, iid3, iid4);

        Set<DTXLogicalTXProviderType> providerTypes = Sets.newHashSet(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,DTXLogicalTXProviderType.NETCONF_TX_PROVIDER);
        //Create two maps, transaction provider map and nodes map
        Map<DTXLogicalTXProviderType, TxProvider> txProviderMap = Maps.toMap(providerTypes, new Function<DTXLogicalTXProviderType, TxProvider>() {
            @Nullable
            @Override
            public TxProvider apply(@Nullable DTXLogicalTXProviderType dtxLogicalTXProviderType) {
                return dtxLogicalTXProviderType == DTXLogicalTXProviderType.NETCONF_TX_PROVIDER ? new myNetconfTxProvider() : new myDataStoreTxProvider();
            }
        });

        nodesMap = Maps.toMap(providerTypes, new Function<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>>() {
            @Nullable
            @Override
            public Set<InstanceIdentifier<?>> apply(@Nullable DTXLogicalTXProviderType dtxLogicalTXProviderType) {
                return dtxLogicalTXProviderType == DTXLogicalTXProviderType.NETCONF_TX_PROVIDER ?
                        (Set)Sets.newHashSet(netConfNodeId1, netConfNodeId2) : (Set)Sets.newHashSet(dataStoreNodeId1, dataStoreNodeId2);
            }
        });

        mixedDTx = new DtxImpl(txProviderMap, nodesMap, new DTxTransactionLockImpl(txProviderMap));
    }

    /**
     * Test netconf putAndRollbackOnFailure() with successful put
     */
    @Test
    public void testPutAndRollbackOnFailureInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 1;
        List<CheckedFuture<Void, DTxException>> putFutures =
                testClass.testWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.PUT);
        for (CheckedFuture<Void, DTxException> putFuture : putFutures){
            try {
                putFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers putAndRollbackOnFailure() with successful put
     */
    @Test
    public void testPutAndRollbackOnFailureInMixedDTx(){
        int expectedDataSizeInTx = 1;
        List<CheckedFuture<Void, DTxException>> putFutures = testClass.
                testWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.PUT);
        for (CheckedFuture<Void, DTxException> putFuture : putFutures){
            try {
                putFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf putAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testPutAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.PUT, OperationType.READ);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf putAndRollbackOnFailure() with failed put and successful rollback
     */
    @Test
    public void testPutAndRollbackOnFailurePutFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.PUT, OperationType.PUT);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers putAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testPutAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.PUT, OperationType.READ);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers putAndRollbackOnFailure() with failed put and successful rollback
     */
    @Test
    public void testPutAndRollbackOnFailurePutFailRollbackSucceedInMixedDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.PUT, OperationType.PUT);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf putAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testPutAndRollbackOnFailureRollbackFailInNetConfOnlyDTx() {
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackFail(ProviderType.NETCONF, OperationType.PUT);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test mixed providers putAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testPutAndRollbackOnFailureRollbackFailInMixedDTx() {
        CheckedFuture<Void, DTxException> putFuture = testClass.
                testWriteAndRollbackOnFailureRollbackFail(ProviderType.MIX, OperationType.PUT);
        try {
            putFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test thread safety of netconf putAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentPutAndRollbackOnFailureInNetConfOnlyDTx(){
        int expectedDataSizeInIdentifier = 1;
        int numOfThreads = (int)(Math.random() * 4) + 1;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.PUT, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ",numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size ", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers putAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentPutAndRollbackOnFailureInMixedDTx(){
        int expectedDataSizeInIdentifier = 1;
        int numOfThreads = (int)(Math.random() * 4) + 1;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.PUT, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ",numOfThreads, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx1",numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of netconf putAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentPutAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.PUT,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size",numOfThreads - 1, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size ", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers putAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentPutAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.PUT,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ", numOfThreads - 1, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx1", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier,
                    internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier,
                    internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf putAndRollbackOnFailure(). One of threads fail to read and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentPutToSameIidReadFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.PUT, OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size ", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }
    /**
     * Test netconf putAndRollbackOnFailure(). One of threads fail to put and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentPutToSameIidPutFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.PUT, OperationType.PUT, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size ", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers putAndRollbackOnFailure(). One of threads fail to read and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentPutToSameIidReadFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int) (Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.PUT, OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1, mixedDTx.getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                dataStoreNodeId1));
        Assert.assertEquals("Wrong data size in tx",
                expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers putAndRollbackOnFailure(). One of threads fail to put and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentPutToSameIidPutFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.PUT, OperationType.PUT, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType
                (DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        Assert.assertEquals("Wrong data size ",
                expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf mergeAndRollbackOnFailure() with successful merge
     */
    @Test
    public void testMergeAndRollbackOnFailureInNetConfOnlyDTx()  {
        int expectedDataSizeInTx = 1;
        List<CheckedFuture<Void, DTxException>> mergeFutures = testClass.
                testWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.MERGE);
        for (CheckedFuture<Void, DTxException> mergeFuture : mergeFutures){
            try{
                mergeFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure() with successful merge
     */
    @Test
    public void testMergeAndRollbackOnFailureInMixedDTx()  {
        int expectedDataSizeInTx = 1;
        List<CheckedFuture<Void, DTxException>> mergeFutures = testClass.
                testWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.MERGE);
        for (CheckedFuture<Void, DTxException> mergeFuture : mergeFutures){
            try{
                mergeFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf mergeAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.MERGE, OperationType.READ);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf mergeAndRollbackOnFailure() with failed merge and successful rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureMergeFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.MERGE, OperationType.MERGE);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.MERGE, OperationType.READ);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure() with failed merge and successful rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureMergeFailRollbackSucceedInMixedDTx() {
        int expectedDataSizeInTx = 0;
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.MERGE, OperationType.MERGE);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf mergeAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureRollbackFailInNetConfOnlyDTx() {
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackFail(ProviderType.NETCONF, OperationType.MERGE);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testMergeAndRollbackOnFailureRollbackFailInMixedDTx() {
        CheckedFuture<Void, DTxException> mergeFuture = testClass.
                testWriteAndRollbackOnFailureRollbackFail(ProviderType.MIX, OperationType.MERGE);
        try {
            mergeFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test thread safety of netconf mergeAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentMergeAndRollbackOnFailureInNetConfOnlyDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.MERGE, numOfThreads);
        Assert.assertEquals("Wrong cache size",numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers mergeAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentMergeAndRollbackOnFailureInMixedDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.MERGE, numOfThreads);
        Assert.assertEquals("Wrong cache size",
                numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of netconf mergeAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentMergeAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx() {
        int numOfThreads = (int)(Math.random()*4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.MERGE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Size of data in the transaction is wrong",numOfThreads - 1, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("size of identifier's data is wrong", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers mergeAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentMergeAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.MERGE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ",numOfThreads - 1, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx1",
                numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf mergeAndRollbackOnFailure(). One of threads fail to read and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentMergeToSameIidReadFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.MERGE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf mergeAndRollbackOnFailure(). One of threads fail to merge and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentMergeToSameIidMergeFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.MERGE,
                OperationType.MERGE, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure(). One of threads fail to read and DTx successfully rollback for same iid
     */
    @Test
    public void testConcurrentMergeToSameIidReadFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.MERGE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers mergeAndRollbackOnFailure(). One of threads fail to merge and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentMergeToSameIidMergeFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.MERGE,
                OperationType.MERGE, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf deleteAndRollbackOnFailure() with successful delete
     */
    @Test
    public void testDeleteAndRollbackOnFailureInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 0;
        List<CheckedFuture<Void, DTxException>> deleteFutures = testClass.
                testWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.DELETE);
        for (CheckedFuture<Void, DTxException> deleteFuture : deleteFutures){
            try{
                deleteFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure() with successful delete
     */
    @Test
    public void testDeleteAndRollbackOnFailureInMixedDTx() {
        int expectedDataSizeInTx = 0;
        List<CheckedFuture<Void, DTxException>> deleteFutures = testClass.
                testWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.DELETE);
        for (CheckedFuture<Void, DTxException> deleteFuture : deleteFutures){
            try{
                deleteFuture.checkedGet();
            }catch (Exception e){
                fail("Caught unexpected exception");
            }
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf deleteAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 1;
        CheckedFuture<Void, DTxException> deleteFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.DELETE, OperationType.READ);
        try {
            deleteFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf deleteAndRollbackOnFailure () with failed delete and successful rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureDeleteFailRollbackSucceedInNetConfOnlyDTx() {
        int expectedDataSizeInTx = 1;
        CheckedFuture<Void, DTxException> deleteFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.DELETE, OperationType.DELETE);
        try {
            deleteFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure() with failed read and successful rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx(){
        int expectedDataSizeInTx = 1;
        CheckedFuture<Void, DTxException> deleteFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.DELETE, OperationType.READ);
        try {
            deleteFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure () failed delete and successful rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureDeleteFailRollbackSucceedInMixedDTx(){
        int expectedDataSizeInTx = 1;
        CheckedFuture<Void, DTxException> deleteFuture = testClass.
                testWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.DELETE, OperationType.DELETE);
        try {
            deleteFuture.checkedGet();
        }catch (Exception e){
            Assert.assertTrue("Can't get EditFailedException", e instanceof DTxException.EditFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInTx, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInTx, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf deleteAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureRollbackFailInNetConfOnlyDTx() {
        internalDtxNetconfTestTx2.createObjForIdentifier(iid1);
        testClass.testWriteAndRollbackOnFailureRollbackFail(ProviderType.NETCONF, OperationType.DELETE);
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure() with failed rollback
     */
    @Test
    public void testDeleteAndRollbackOnFailureRollbackFailInMixedDTx(){
        internalDtxDataStoreTestTx2.createObjForIdentifier(iid1);
        testClass.testWriteAndRollbackOnFailureRollbackFail(ProviderType.MIX, OperationType.DELETE);
    }

    /**
     * Test thread safety of netconf deleteAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentDeleteAndRollbackOnFailureInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.NETCONF, OperationType.DELETE, numOfThreads);
        Assert.assertEquals("Wrong cache size",numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers deleteAndRollbackOnFailure()
     */
    @Test
    public void testConcurrentDeleteAndRollbackOnFailureInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndRollbackOnFailure(ProviderType.MIX, OperationType.DELETE, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ",numOfThreads, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx1", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx", expectedDataSizeInIdentifier,
                    internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx", expectedDataSizeInIdentifier,
                    internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of netconf deleteAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentDeleteAndRollbackOnFailureReadFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.NETCONF, OperationType.DELETE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size in tx",numOfThreads - 1,netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test thread safety of mixed providers deleteAndRollbackOnFailure() with failed read and successful rollback for different iids
     */
    @Test
    public void testConcurrentDeleteAndRollbackOnFailureReadFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndRollbackOnFailureRollbackSucceed(ProviderType.MIX, OperationType.DELETE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size in netConf tx1 ", numOfThreads - 1, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx1", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER,
                dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier,
                    internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier,
                    internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf deleteAndRollbackOnFailure(). One of threads fail to read and DTx rollback successfully for same iid
     */
    @Test
    public void testConcurrentDeleteToSameIidReadFailRollbackSucceedInNetConfDTx(){
        int numOfThreads = (int) (Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.DELETE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1 , netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure(). One of threads fail to read and DTx rollback successfully for same iid in
     */
    @Test
    public void testConcurrentDeleteToSameIidReadFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.DELETE,
                OperationType.READ, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads - 1, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf deleteAndRollbackOnFailure(). One of threads fail to delete and DTx rollback successfully
     */
    @Test
    public void testConcurrentDeleteToSameIidDeleteFailRollbackSucceedInNetConfOnlyDTx(){
        int numOfThreads = (int) (Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.NETCONF, OperationType.DELETE,
                OperationType.DELETE, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers deleteAndRollbackOnFailure(). One of threads fail to delete and DTx rollback successfully
     */
    @Test
    public void testConcurrentDeleteToSameIidDeleteFailRollbackSucceedInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteToSameIidRollbackSucceed(ProviderType.MIX, OperationType.DELETE,
                OperationType.DELETE, numOfThreads);
        Assert.assertEquals("Wrong cache size", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf submit() with multiple threads to successfully put data to nodes
     */
    @Test
    public void testConcurrentPutAndSubmitInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndSubmit(ProviderType.NETCONF, OperationType.PUT, numOfThreads);
        netConfOnlyDTx.submit();
        for (final InstanceIdentifier<?> nodeIid : netconfNodes) {
            Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(nodeIid));
            for (int i = 0; i < numOfThreads; i++) {
                Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            }
        }
    }

    /**
     * Test mixed providers submit() with multiple threads to successfully put data to nodes
     */
    @Test
    public void testConcurrentPutAndSubmitInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndSubmit(ProviderType.MIX, OperationType.PUT, numOfThreads);
        mixedDTx.submit();
        for (InstanceIdentifier<?> nodeId : netconfNodes){
            Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodeId
            ));
        }
        for (InstanceIdentifier<?> nodeId : dataStoreNodes){
            Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads,mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, nodeId
            ));
        }
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInIdentifier, internalDtxNetconfTestTx2.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx2.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf submit() with multiple threads to successfully merge data to nodes
     */
    @Test
    public void testConcurrentMergeAndSubmitInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndSubmit(ProviderType.NETCONF, OperationType.MERGE, numOfThreads);
        netConfOnlyDTx.submit();
        for (final InstanceIdentifier<?> nodeIid : netconfNodes) {
            Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(nodeIid));
            for (int i = 0; i < numOfThreads; i++) {
                Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            }
        }
    }

    /**
     * Test mixed providers submit() with multiple threads to successfully merge data to nodes
     */
    @Test
    public void testConcurrentMergeAndSubmitInMixedDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 1;
        testClass.testConcurrentWriteAndSubmit(ProviderType.MIX, OperationType.MERGE, numOfThreads);
        mixedDTx.submit();
        for (InstanceIdentifier<?> nodeId : netconfNodes){
            Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodeId
            ));
        }
        for (InstanceIdentifier<?> nodeId : dataStoreNodes){
            Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, nodeId
            ));
        }
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInIdentifier, internalDtxNetconfTestTx2.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx2.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf submit() with multiple threads to successfully delete data in nodes
     */
    @Test
    public void testConcurrentDeleteAndSubmitInNetConfOnlyDTx(){
        int numOfThreads = (int)(Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndSubmit(ProviderType.NETCONF, OperationType.DELETE, numOfThreads);
        netConfOnlyDTx.submit();
        for (final InstanceIdentifier<?> nodeIid : netconfNodes) {
            Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(nodeIid));
        }
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInIdentifier, internalDtxNetconfTestTx2.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test mixed providers submit() with multiple threads to successfully delete data in nodes
     */
    @Test
    public void testConcurrentDeleteAndSubmitInMixedDTx(){
        int numOfThreads = (int) (Math.random() * 3) + 1;
        int expectedDataSizeInIdentifier = 0;
        testClass.testConcurrentWriteAndSubmit(ProviderType.MIX, OperationType.DELETE, numOfThreads);
        mixedDTx.submit();
        for (InstanceIdentifier<?> nodeId : netconfNodes){
            Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads,mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodeId
            ));
        }
        for (InstanceIdentifier<?> nodeId : dataStoreNodes){
            Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads,mixedDTx.getSizeofCacheByNodeIdAndType(
                    DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, nodeId
            ));
        }
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInIdentifier, internalDtxNetconfTestTx2.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Data size indataStore tx2 is wrong", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx2.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf rollback() with successful rollback
     */
    @Test
    public void testRollbackInNetConfOnlyDTx() {
        int expectedDataSizeInTx1 = 0, expectedDataSizeInTx2 = 0;
        CheckedFuture<Void, DTxException> f1 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);

        try {
            f1.checkedGet();
            f2.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        CheckedFuture<Void, DTxException.RollbackFailedException> f = netConfOnlyDTx.rollback();
        try{
            f.checkedGet();
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx1, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
            Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx2, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        }catch (Exception e) {
            fail("Get rollback exception");
        }
    }

    /**
     * Test mixed providers rollback() with successful rollback
     */
    @Test
    public void testRollbackInMixedDTx() {
        int expectedDataSizeInNetConfTx1 = 0, expectedDataSizeInNetConfTx2 = 0, expectedDataSizeInDataStoreTx1 = 0, expectedDataSizeInDataStoreTx2 = 0;
        CheckedFuture<Void, DTxException> f1 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);
        CheckedFuture<Void, DTxException> f3 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId1);
        CheckedFuture<Void, DTxException> f4 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId2);

        try{
            f1.checkedGet();
            f2.checkedGet();
            f3.checkedGet();
            f4.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFut = mixedDTx.rollback();
        try{
            rollbackFut.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInNetConfTx1, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInNetConfTx2, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInDataStoreTx1, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInDataStoreTx2, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf rollback() with failed rollback
     */
    @Test
    public void testRollbackFailInNetConfOnlyDTx()
    {
        CheckedFuture<Void, DTxException> f1 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);
        try{
            f1.checkedGet();
            f2.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }
        internalDtxNetconfTestTx2.setSubmitException(true);
        CheckedFuture<Void, DTxException.RollbackFailedException> f = netConfOnlyDTx.rollback();
        try {
            f.checkedGet();
            fail("Can't get exception");
        }catch (Exception e) {
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test mixed providers rollback() with failed rollback
     */
    @Test
    public void testRollbackFailInMixedDTx()
    {
        CheckedFuture<Void, DTxException> f1 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);
        CheckedFuture<Void, DTxException> f3 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId1);
        CheckedFuture<Void, DTxException> f4 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId2);
        try{
            f1.checkedGet();
            f2.checkedGet();
            f3.checkedGet();
            f4.checkedGet();
        }catch (Exception e) {
            fail("Caught unexpected exception");
        }

        internalDtxDataStoreTestTx2.setSubmitException(true);
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFut = mixedDTx.rollback();
        try{
            rollbackFut.checkedGet();
            fail("Can't get exception");
        }catch (Exception e) {
            Assert.assertTrue("Can't get RollbackFailedException", e instanceof DTxException.RollbackFailedException);
        }
    }

    /**
     * Test rollback(). netconf DTx wait for all put actions to finish before performing rollback
     */
    @Test
    public void testConcurrentPutAndRollbackInNetConfOnlyDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.NETCONF, OperationType.PUT, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size ", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test rollback(). Mixed providers DTx wait for all put actions finish before performing rollback
     */
    @Test
    public void testConcurrentPutAndRollbackInMixedDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.MIX, OperationType.PUT, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size in netConf tx", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test rollback(). netconf DTx wait for all merge actions to finish before performing rollback
     */
    @Test
    public void testConcurrentMergeAndRollbackInNetConfOnlyDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.NETCONF, OperationType.MERGE, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test rollback(). Mixed providers DTx wait for all merge actions finish before performing rollback
     */
    @Test
    public void testConcurrentMergeAndRollbackInMixedDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 0;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.MIX, OperationType.MERGE, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++)
        {
            Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test rollback(). netconf DTx  wait for all delete actions finish before performing rollback
     */
    @Test
    public void testConcurrentDeleteAndRollbackInNetConfOnlyDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.NETCONF, OperationType.DELETE, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size", numOfThreads, netConfOnlyDTx.getSizeofCacheByNodeId(netConfNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong data size", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test rollback(). Mixed providers DTx wait for all delete actions finish before performing rollback
     */
    @Test
    public void testConcurrentDeleteAndRollbackInMixedDTx() {
        int numOfThreads = (int)(Math.random() * 4) + 1;
        int expectedDataSizeInIdentifier = 1;
        CheckedFuture<Void, DTxException.RollbackFailedException> rollbackFuture = testClass.
                testConcurrentWriteAndRollback(ProviderType.MIX, OperationType.DELETE, numOfThreads);
        try {
            rollbackFuture.checkedGet();
        } catch (Exception e) {
            fail("Get rollback exception");
        }
        Assert.assertEquals("Wrong cache size in netConf tx", numOfThreads, mixedDTx.getSizeofCacheByNodeId(netConfNodeId1));
        Assert.assertEquals("Wrong cache size in dataStore tx", numOfThreads, mixedDTx.getSizeofCacheByNodeIdAndType(
                DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, dataStoreNodeId1));
        for (int i = 0; i < numOfThreads; i++) {
            Assert.assertEquals("Wrong cache size in netConf tx1", expectedDataSizeInIdentifier, internalDtxNetconfTestTx1.getTxDataSizeByIid(identifiers.get(i)));
            Assert.assertEquals("Wrong cache size in dataStore tx1", expectedDataSizeInIdentifier, internalDtxDataStoreTestTx1.getTxDataSizeByIid(identifiers.get(i)));
        }
    }

    /**
     * Test netconf submit() with successful submit
     */
    @Test
    public void testSubmitInNetConfOnlyDTx() {
        CheckedFuture<Void, TransactionCommitFailedException> f = netConfOnlyDTx.submit();
        try {
            f.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }
    }

    /**
     * Test mixed providers submit() with successful submit
     */
    @Test
    public void testSubmitInMixedDTx() {
        CheckedFuture<Void, TransactionCommitFailedException> f = mixedDTx.submit();
        try{
            f.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }
    }

    /**
     * Test netconf submit() with failed submit and successful rollback
     */
    @Test
    public void testSubmitRollbackSucceedInNetConfOnlyDTx()  {
        int expectedDataSizeInTx1 = 0, expectedDataSizeInTx2 = 0;
        CheckedFuture<Void, DTxException> f1 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);
        try {
            f1.checkedGet();
            f2.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }

        internalDtxNetconfTestTx2.setSubmitException(true);
        CheckedFuture<Void, TransactionCommitFailedException> f = netConfOnlyDTx.submit();
        try{
            f.checkedGet();
        }catch (Exception e) {
            Assert.assertTrue("Can't get TransactionCommitFailedException",e instanceof TransactionCommitFailedException);
        }

        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInTx1, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInTx2, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test mixed providers submit() with failed submit and successful rollback
     */
    @Test
    public void testSubmitRollbackSucceedInMixedDTx()  {
        int expectedDataSizeInNetConfTx1 = 0, expectedDataSizeInNetConfTx2 = 0, expectedDataSizeInDataStoreTx1 = 0, expectedDataSizeInDataStoreTx2 = 0;
        CheckedFuture<Void, DTxException> f1 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), netConfNodeId2);
        CheckedFuture<Void, DTxException> f3 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId1);
        CheckedFuture<Void, DTxException> f4 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId2);
        try{
            f1.checkedGet();
            f2.checkedGet();
            f3.checkedGet();
            f4.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }
        internalDtxDataStoreTestTx2.setSubmitException(true);
        CheckedFuture<Void, TransactionCommitFailedException> f = mixedDTx.submit();
        try{
            f.checkedGet();
            fail("Can't get exception");
        }catch (Exception e) {
            Assert.assertTrue("Can't get TransactionCommitFailedException", e instanceof TransactionCommitFailedException);
        }
        Assert.assertEquals("Wrong data size in netConf tx1", expectedDataSizeInNetConfTx1, internalDtxNetconfTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in netConf tx2", expectedDataSizeInNetConfTx2, internalDtxNetconfTestTx2.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx1", expectedDataSizeInDataStoreTx1, internalDtxDataStoreTestTx1.getTxDataSizeByIid(iid1));
        Assert.assertEquals("Wrong data size in dataStore tx2", expectedDataSizeInDataStoreTx2, internalDtxDataStoreTestTx2.getTxDataSizeByIid(iid1));
    }

    /**
     * Test netconf submit() with failed rollback
     */
    @Test
    public void testSubmitRollbackFailInNetConfOnlyDTx()
    {
        internalDtxNetconfTestTx2.setSubmitException(true);
        internalDtxNetconfTestTx2.setDeleteExceptionByIid(iid1,true);

        CheckedFuture<Void, DTxException> f1 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1,new TestIid1(), netConfNodeId1);
        CheckedFuture<Void, DTxException> f2 = netConfOnlyDTx.putAndRollbackOnFailure(LogicalDatastoreType.OPERATIONAL, iid1,new TestIid1(), netConfNodeId2);
        try {
            f1.checkedGet();
            f2.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }

        CheckedFuture<Void, TransactionCommitFailedException> f = netConfOnlyDTx.submit();
        try{
            f.checkedGet();
        }catch (Exception e) {
            Assert.assertTrue("Can't get TransactionCommitFailedException",e instanceof TransactionCommitFailedException );
        }
    }

    /**
     * test mixed providers submit() with failed rollback
     */
    @Test
    public void testSubmitRollbackFailInMixedDTx()
    {
        internalDtxDataStoreTestTx2.setSubmitException(true);
        internalDtxDataStoreTestTx2.setDeleteExceptionByIid(iid1, true);
        CheckedFuture<Void, DTxException> f1 = mixedDTx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.OPERATIONAL, iid1, new TestIid1(), dataStoreNodeId2);
        try{
            f1.checkedGet();
        }catch (Exception e) {
            fail("Get exception");
        }

        CheckedFuture<Void, TransactionCommitFailedException> f2 = mixedDTx.submit();
        try{
            f2.checkedGet();
        }catch (Exception e) {
            Assert.assertTrue("Can't get TransactionCommitFailedException", e instanceof TransactionCommitFailedException);
        }
    }
    /* TODO: Test rollback() error cases */
}