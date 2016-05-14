/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.spi.*;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DtxImpl implements DTx {
    private static final Logger LOG = LoggerFactory.getLogger(DTxProviderImpl.class);
    private final Map<DTXLogicalTXProviderType, Map<InstanceIdentifier<?>, CachingReadWriteTx>> perNodeTransactionsbyLogicalType;
    private final Map<DTXLogicalTXProviderType, TxProvider>txProviderMap;
    private final Map<InstanceIdentifier<?>, ReadWriteTransaction> readWriteTxMap= new HashMap<InstanceIdentifier<?>, ReadWriteTransaction>();
    private final TransactionLock deviceLock;

    public DtxImpl(@Nonnull final TxProvider txProvider, @Nonnull final Set<InstanceIdentifier<?>> nodes, TransactionLock lock) {
        Preconditions.checkArgument(!nodes.isEmpty(), "Cannot create distributed tx for 0 nodes");
        txProviderMap = new HashMap<>();
        this.txProviderMap.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, txProvider);
        Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> internalNodeMap = new HashMap<>(1);
        internalNodeMap.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodes);
        this.perNodeTransactionsbyLogicalType =  initializeTransactionsPerLogicalType(this.txProviderMap, internalNodeMap);
        this.deviceLock = lock;
    }

    public DtxImpl(@Nonnull final Map<DTXLogicalTXProviderType, TxProvider> providerMap,
                   @Nonnull final Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap, TransactionLock lock) {
        Preconditions.checkArgument(!nodesMap.values().isEmpty(), "Cannot create distributed tx for 0 nodes");
        Preconditions.checkArgument(providerMap.keySet().containsAll(nodesMap.keySet()), "logicalType sets of txporiders and nodes are different");
        this.txProviderMap = providerMap;
        perNodeTransactionsbyLogicalType = initializeTransactionsPerLogicalType(providerMap, nodesMap);
        this.deviceLock = lock;
    }

    private TxProvider getTxProviderByType(DTXLogicalTXProviderType type){
        return this.txProviderMap.get(type);
    }

    private Map<DTXLogicalTXProviderType, Map<InstanceIdentifier<?>, CachingReadWriteTx>> initializeTransactionsPerLogicalType(final Map<DTXLogicalTXProviderType, TxProvider>txProviderMap,
                                                                                                Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap){
        Map<DTXLogicalTXProviderType, Map<InstanceIdentifier<?>, CachingReadWriteTx>> typeCacheMap = new HashMap<>(txProviderMap.keySet().size());

        for(DTXLogicalTXProviderType type : nodesMap.keySet()){
            Set<InstanceIdentifier<?>> nodes = nodesMap.get(type);
            final DTXLogicalTXProviderType t = type;
            Map<InstanceIdentifier<?>, CachingReadWriteTx>tmpMap = Maps.toMap(nodes, new Function<InstanceIdentifier<?>, CachingReadWriteTx>() {
                @Nullable @Override public CachingReadWriteTx apply(@Nullable final InstanceIdentifier<?> input) {
                    ReadWriteTransaction tx = getTxProviderByType(t).newTx(input);
                    readWriteTxMap.put(input, tx);
                    return new CachingReadWriteTx(tx);
                }
            });
            typeCacheMap.put(type, tmpMap);
        }

        return typeCacheMap;
    }

    // This is a method for unit test
    public int getSizeofCacheByNodeId(InstanceIdentifier<?> nodeId)
    {
        Preconditions.checkArgument(perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).containsKey(nodeId),
                "Unknown node: %s. Not in transaction", nodeId);
        return getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodeId);
    }

    // This is a method for unit test
    public int getSizeofCacheByNodeIdAndType(DTXLogicalTXProviderType type, InstanceIdentifier<?> nodeId)
    {
        Preconditions.checkArgument(containsIid(nodeId), "Unknown node: %s. Not in transaction", nodeId);
        return perNodeTransactionsbyLogicalType.get(type).get(nodeId).getSizeOfCache();
    }

    @Deprecated
    @Override public void delete(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<?> instanceIdentifier, final InstanceIdentifier<?> nodeId)
        throws DTxException.EditFailedException {

        Preconditions.checkArgument(perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).containsKey(nodeId),
                "Unknown node: %s. Not in transaction", nodeId);
        final ReadWriteTransaction transaction = perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).get(nodeId);
        transaction.delete(logicalDatastoreType, instanceIdentifier);
    }

    @Deprecated
    @Override public void delete(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<?> instanceIdentifier) throws DTxException.EditFailedException {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Deprecated
    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t)
        throws DTxException.EditFailedException, DTxException.RollbackFailedException {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Deprecated
    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b)
        throws DTxException.EditFailedException {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Deprecated
    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t) throws DTxException.EditFailedException {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Deprecated
    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b)
        throws DTxException.EditFailedException {
        throw new UnsupportedOperationException("Unimplemented");

    }

    @Deprecated
    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId)
        throws DTxException.EditFailedException {
            Preconditions.checkArgument(perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).containsKey(nodeId),
                    "Unknown node: %s. Not in transaction", nodeId);
            final ReadWriteTransaction transaction = perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).get(nodeId);
            transaction.merge(logicalDatastoreType, instanceIdentifier, t);
    }

    @Deprecated
    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b, final InstanceIdentifier<?> nodeId)
        throws DTxException.EditFailedException {

    }

    @Deprecated
    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId)
        throws DTxException.EditFailedException {
        Preconditions.checkArgument(perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).containsKey(nodeId),
                "Unknown node: %s. Not in transaction", nodeId);
        final ReadWriteTransaction transaction = perNodeTransactionsbyLogicalType.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER).get(nodeId);
        transaction.put(logicalDatastoreType, instanceIdentifier, t);
    }

    @Deprecated
    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b, final InstanceIdentifier<?> nodeId)
        throws DTxException.EditFailedException {

    }

    @Override public CheckedFuture<Void, TransactionCommitFailedException> submit()
        throws DTxException.SubmitFailedException, DTxException.RollbackFailedException {

        int totalSubmitSize = getNumberofNodes();

        final Map<InstanceIdentifier<?>, PerNodeTxState> commitStatus = Maps.newHashMapWithExpectedSize(totalSubmitSize);
        final SettableFuture<Void> distributedSubmitFuture = SettableFuture.create();

        for(DTXLogicalTXProviderType type: this.perNodeTransactionsbyLogicalType.keySet()) {
            Map<InstanceIdentifier<?>, CachingReadWriteTx> transactions = this.perNodeTransactionsbyLogicalType.get(type);

            for (final Map.Entry<InstanceIdentifier<?>, CachingReadWriteTx> perNodeTx : transactions.entrySet()) {
                CheckedFuture<Void, TransactionCommitFailedException> submitFuture = null;
                try {
                    submitFuture = perNodeTx.getValue().submit();
                }catch (Exception submitFailException){
                    new PerNodeSubmitCallback(type, commitStatus, perNodeTx, distributedSubmitFuture).failedWithException(submitFailException);
                    continue;
                }
                Futures.addCallback(submitFuture, new PerNodeSubmitCallback(type, commitStatus, perNodeTx, distributedSubmitFuture));
            }
        }

        return Futures.makeChecked(distributedSubmitFuture, new Function<Exception, TransactionCommitFailedException>() {
            @Nullable @Override public TransactionCommitFailedException apply(@Nullable final Exception input) {
                return new TransactionCommitFailedException("Submit failed. Check nested exception for rollback status", input);
            }
        });
    }

    /**
     * Perform submit rollback with the caches and empty rollback transactions for every node
     */
    private CheckedFuture<Void, DTxException.RollbackFailedException> rollbackUponCommitFailure(
        final Map<InstanceIdentifier<?>, PerNodeTxState> commitStatus) {

        Map<InstanceIdentifier<?>, CachingReadWriteTx> perNodeCache = new HashMap<>();

        for(DTXLogicalTXProviderType type : this.perNodeTransactionsbyLogicalType.keySet()) {
            Map<InstanceIdentifier<?>, CachingReadWriteTx> tmpMap = this.perNodeTransactionsbyLogicalType.get(type);
            perNodeCache.putAll(tmpMap);
        }

        Rollback rollback = new RollbackImpl();
        final ListenableFuture<Void> rollbackFuture = rollback.rollback(perNodeCache,
            Maps.transformValues(commitStatus, new Function<PerNodeTxState, ReadWriteTransaction>() {
                @Nullable @Override public ReadWriteTransaction apply(@Nullable final PerNodeTxState input) {
                    return input.getRollbackTx();
                }
            }));

        return Futures.makeChecked(rollbackFuture, new Function<Exception, DTxException.RollbackFailedException>() {
            @Nullable @Override public DTxException.RollbackFailedException apply(@Nullable final Exception input) {
                return new DTxException.RollbackFailedException(input);
            }
        });
    }

    private CheckedFuture<Void, DTxException.RollbackFailedException> rollbackUponOperationFailure(){
        Rollback rollback = new RollbackImpl();
        Map<InstanceIdentifier<?>, CachingReadWriteTx> perNodeCache = new HashMap<>();

        for(DTXLogicalTXProviderType type : this.perNodeTransactionsbyLogicalType.keySet()) {
            Map<InstanceIdentifier<?>, CachingReadWriteTx> tmpMap = this.perNodeTransactionsbyLogicalType.get(type);
            perNodeCache.putAll(tmpMap);
        }

        final ListenableFuture<Void> rollbackFuture= rollback.rollback(perNodeCache, this.readWriteTxMap);

        return Futures.makeChecked(rollbackFuture, new Function<Exception, DTxException.RollbackFailedException>() {
            @Nullable @Override public DTxException.RollbackFailedException apply(@Nullable final Exception input) {
                return new DTxException.RollbackFailedException(input);
            }
        });
    }

    private void dtxReleaseDevices(){
        Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> devices = Maps.transformValues(perNodeTransactionsbyLogicalType, new Function<Map<InstanceIdentifier<?>, CachingReadWriteTx>, Set<InstanceIdentifier<?>>>() {
            @Nullable
            @Override
            public Set<InstanceIdentifier<?>> apply(@Nullable Map<InstanceIdentifier<?>, CachingReadWriteTx> input) {
                return input.keySet();
            }
        });
        deviceLock.releaseDevices(devices);
    }

    @Deprecated
    @Override public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        throw new UnsupportedOperationException("Deprecated");
    }

    @Override public boolean cancel() throws DTxException.RollbackFailedException {
        throw new UnsupportedOperationException("Deprecated");
    }

    @Override public Object getIdentifier() {
        return getIdentifierSet();
    }

    private Set<InstanceIdentifier<?>> getIdentifierSet(){
        Set<InstanceIdentifier<?>> set = new HashSet<>();

        for(DTXLogicalTXProviderType type : DTXLogicalTXProviderType.values()){
            if(this.perNodeTransactionsbyLogicalType.containsKey(type)){
                set.addAll(this.perNodeTransactionsbyLogicalType.get(type).keySet());
            }
        }

        return set;
    }

    private int getNumberofNodes(){
        int totalSubmitSize = 0;

        for(DTXLogicalTXProviderType type : this.perNodeTransactionsbyLogicalType.keySet()){
            totalSubmitSize += perNodeTransactionsbyLogicalType.get(type).size();
        }

        return totalSubmitSize;
    }

    private TxProvider getTxProviderByLogicalType(DTXLogicalTXProviderType type){
        Preconditions.checkArgument(this.txProviderMap.containsKey(type), "can't find key");
        return this.txProviderMap.get(type);
    }

    private class PerNodeSubmitCallback implements FutureCallback<Void> {
        private final Map<InstanceIdentifier<?>, PerNodeTxState> commitStatus;
        private final Map.Entry<InstanceIdentifier<?>, CachingReadWriteTx> perNodeTx;
        private final SettableFuture<Void> distributedSubmitFuture;
        private final DTXLogicalTXProviderType logicalTxProviderType;
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        public PerNodeSubmitCallback(DTXLogicalTXProviderType type, final Map<InstanceIdentifier<?>, PerNodeTxState> commitStatus,
            final Map.Entry<InstanceIdentifier<?>, CachingReadWriteTx> perNodeTx,
            final SettableFuture<Void> distributedSubmitFuture) {
            this.commitStatus = commitStatus;
            this.perNodeTx = perNodeTx;
            this.distributedSubmitFuture = distributedSubmitFuture;
            logicalTxProviderType = type;
        }

        /**
         * Callback for per-node transaction submit success
         * Prepare potential rollback per-node transaction (rollback will be performed in a dedicated Tx)
         */
        @Override public void onSuccess(@Nullable final Void result) {
            LOG.trace("Per node tx({}/{}) executed successfully for: {}",
                    commitStatus.size(), getNumberofNodes(), perNodeTx.getKey());

            final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(executor);

            final ListenableFuture txProviderFuture = executorService.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    final ReadWriteTransaction readWriteTransaction = getTxProviderByLogicalType(logicalTxProviderType).newTx(perNodeTx.getKey());
                    final PerNodeTxState status = PerNodeTxState.createSuccess(readWriteTransaction);
                    synchronized (commitStatus) {
                        commitStatus.put(perNodeTx.getKey(), status);
                    }
                    checkTransactionStatus();
                    return null;
                }
            });

            Futures.addCallback(txProviderFuture, new FutureCallback() {
                @Override
                public void onSuccess(@Nullable Object result) {
                    LOG.trace("Per node new tx succefully");
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.trace("Per node error to relock the device. ignore");
                }
            });
        }

        /**
         * Invoked when this distributed transaction cannot open a post submit transaction (for performing potential rollback)
         */
        private void handleRollbackTxCreationException(final TxException.TxInitiatizationFailedException e) {
            LOG.warn("Unable to create post submit transaction for node: {}. Distributed transaction failing",
                perNodeTx.getKey(), e);
            distributedSubmitFuture.setException(new DTxException.SubmitFailedException(
                Collections.<InstanceIdentifier<?>>singleton(perNodeTx.getKey()), e));
        }

        /**
         * Callback for per-node transaction submit FAIL
         * Prepare rollback per-node transaction (rollback will be performed in a dedicated Tx)
         */
        @Override public void onFailure(final Throwable t) {
            failedWithException(t);
        }

        public void failedWithException(final Throwable t) {
            LOG.warn("Per node tx executed failed for: {}", perNodeTx.getKey(), t);

            final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(executor);

            final ListenableFuture txProviderFuture = executorService.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    try {
                        final ReadWriteTransaction readWriteTransaction = getTxProviderByLogicalType(logicalTxProviderType).newTx(perNodeTx.getKey());
                        synchronized (commitStatus) {
                            commitStatus.put(perNodeTx.getKey(), PerNodeTxState.createFailed(t, readWriteTransaction));
                        }
                        checkTransactionStatus();

                    } catch (TxException.TxInitiatizationFailedException e) {
                        handleRollbackTxCreationException(e);
                    }
                    return null;
                }
            });
        }

        /**
         * Check the overall status of distributed Tx after each per-node transaction status change
         */
        private void checkTransactionStatus() {
            try {
                final DistributedSubmitState txState = validate(commitStatus, getIdentifierSet());

                switch (txState) {
                    case WAITING: {
                        return;
                    }
                    case SUCCESS: {
                        this.releaseTx();
                        deviceLock.releaseDevices(logicalTxProviderType, this.commitStatus.keySet());
                        distributedSubmitFuture.set(null);
                        return;
                    }
                    default: {
                        throw new IllegalArgumentException("Unsupported " + txState);
                    }
                }

            } catch (final DTxException.SubmitFailedException e) {
                Futures.addCallback(rollbackUponCommitFailure(commitStatus), new FutureCallback<Void>() {
                    @Override public void onSuccess(@Nullable final Void result) {
                        LOG.trace("Distributed tx failed for {}. Rollback was successful", perNodeTx.getKey());
                        deviceLock.releaseDevices(logicalTxProviderType, commitStatus.keySet());
                        distributedSubmitFuture.setException(e);
                    }

                    @Override public void onFailure(final Throwable t) {
                        LOG.warn("Distributed tx filed. Rollback FAILED. Device(s) state is unknown", t);
                        deviceLock.releaseDevices(logicalTxProviderType, commitStatus.keySet());
                        distributedSubmitFuture.setException(t);
                    }
                });
            }
        }

        /**
         * Validate distributed Tx status. Either waiting, success-all or fail indicated by an exception
         */
        private DistributedSubmitState validate(final Map<InstanceIdentifier<?>, PerNodeTxState> commitStatus,
            final Set<InstanceIdentifier<?>> instanceIdentifiers) throws DTxException.SubmitFailedException {
            boolean submitDone = false;
            synchronized (commitStatus) {
                if (commitStatus.size() == instanceIdentifiers.size())
                    submitDone = true;
            }
            if(submitDone){
                LOG.debug("Distributed tx submit finished with status: {}", commitStatus);
                final Map<InstanceIdentifier<?>, PerNodeTxState> failedSubmits = Maps
                    .filterEntries(commitStatus, new Predicate<Map.Entry<InstanceIdentifier<?>, PerNodeTxState>>() {
                        @Override public boolean apply(final Map.Entry<InstanceIdentifier<?>, PerNodeTxState> input) {
                            return !input.getValue().isSuccess();
                        }
                    });

                if(!failedSubmits.isEmpty()) {
                    throw new DTxException.SubmitFailedException(failedSubmits.keySet());
                } else {
                    return DistributedSubmitState.SUCCESS;
                }
            }

            return DistributedSubmitState.WAITING;
        }

        private void releaseTx(){
            for(Map.Entry<InstanceIdentifier<?>, PerNodeTxState> e : this.commitStatus.entrySet()){
                e.getValue().releaseStatePerNode();
            }
        }
    }

    public enum DistributedSubmitState {
        SUCCESS, FAILED, WAITING;
    }

    /**
     * Per-node transaction state. Generally its success or fail. This also keeps the rollback transaction
     */
    private static final class PerNodeTxState {
        private boolean success;
        @Nullable private Throwable t;
        private final ReadWriteTransaction rollbackTx;
        DTXLogicalTXProviderType logicalTXProviderType;

        public PerNodeTxState(final boolean success, @Nullable final Throwable t, @Nonnull ReadWriteTransaction rollbackTx) {
            this(success, rollbackTx);
            this.t = t;
        }

        public PerNodeTxState(final boolean success, @Nonnull ReadWriteTransaction rollbackTx) {
            this.success = success;
            this.rollbackTx = rollbackTx;
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable public Optional<Throwable> getException() {
            return Optional.fromNullable(t);
        }

        public ReadWriteTransaction getRollbackTx() {
            return rollbackTx;
        }

        public static PerNodeTxState createFailed(final Throwable t, final ReadWriteTransaction readWriteTransaction) {
            return new PerNodeTxState(false, t, readWriteTransaction);
        }

        public static PerNodeTxState createSuccess(final ReadWriteTransaction readWriteTransaction) {
            return new PerNodeTxState(true, readWriteTransaction);
        }

        public void releaseStatePerNode(){
            this.rollbackTx.cancel();
        }
    }

    private boolean containsIid(InstanceIdentifier<?> iid){
        for(DTXLogicalTXProviderType type: this.perNodeTransactionsbyLogicalType.keySet()) {
            if(this.perNodeTransactionsbyLogicalType.get(type).containsKey(iid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId){
        return mergeAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, logicalDatastoreType, instanceIdentifier, t, nodeId);
    }

    public <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId){
        return putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, logicalDatastoreType, instanceIdentifier, t, nodeId);
    }

    public CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(final LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<?> instanceIdentifier,
                                                                               InstanceIdentifier<?> nodeId){
            return this.deleteAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, logicalDatastoreType, instanceIdentifier, nodeId);
    }

    public CheckedFuture<Void, DTxException.RollbackFailedException>  rollback(){
        return this.rollbackUponOperationFailure();
    }

    @Override
    public <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(DTXLogicalTXProviderType logicalTXProviderType,
                                                                                                     LogicalDatastoreType logicalDatastoreType,
                                                                                                     InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) {
        Preconditions.checkArgument(containsIid(nodeId), "Unknown node: %s. Not in transaction", nodeId);
        final DTXReadWriteTransaction transaction = this.perNodeTransactionsbyLogicalType.get(logicalTXProviderType).get(nodeId);

        CheckedFuture<Void, DTxException> mergeFuture = transaction.asyncMerge(logicalDatastoreType, instanceIdentifier, t);

        final SettableFuture<Void> retFuture = SettableFuture.create();

        Futures.addCallback(mergeFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
                retFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        CheckedFuture<Void, DTxException.RollbackFailedException> rollExcept = rollback();
                        Futures.addCallback(rollExcept, new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void aVoid) {
                                dtxReleaseDevices();
                                retFuture.setException(new DTxException.EditFailedException("Failed to merge but succeed to rollback"));
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                dtxReleaseDevices();
                                retFuture.setException(throwable);
                            }
                        });
                    }
                };

                new Thread(runnable).start();
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e = (Exception)e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("Merge failed and rollback failure", e);
            }
        });
    }

    @Override
    public <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(DTXLogicalTXProviderType logicalTXProviderType,
                LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) {
        Preconditions.checkArgument(containsIid(nodeId), "Unknown node: %s. Not in transaction", nodeId);
        final DTXReadWriteTransaction transaction = this.perNodeTransactionsbyLogicalType.get(logicalTXProviderType).get(nodeId);
        Preconditions.checkArgument(containsIid(nodeId), "Unknown node: %s. Not in transaction", nodeId);
        CheckedFuture<Void, DTxException> putFuture = transaction.asyncPut(logicalDatastoreType, instanceIdentifier, t);

        final SettableFuture<Void> retFuture = SettableFuture.create();

        Futures.addCallback(putFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
                retFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.trace("asyncput failure callback begin to roll back ");
                Runnable rolllbackRoutine = new Runnable() {
                    @Override
                    public void run() {
                        CheckedFuture<Void, DTxException.RollbackFailedException> rollExcept = rollback();

                        Futures.addCallback(rollExcept, new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void result) {
                                dtxReleaseDevices();
                                retFuture.setException(new DTxException.EditFailedException("Failed to put but succeed to rollback"));
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                dtxReleaseDevices();
                                retFuture.setException(t);
                            }
                        });
                    }
                };

                new Thread(rolllbackRoutine).start();
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e = (Exception)e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("Put failed and rollback failure", e);
            }
        });
    }

    @Override
    public CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(DTXLogicalTXProviderType logicalTXProviderType,
                    LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier, InstanceIdentifier<?> nodeId) {

        Preconditions.checkArgument(containsIid(nodeId), "Unknown node: %s. Not in transaction", nodeId);
        final DTXReadWriteTransaction transaction = this.perNodeTransactionsbyLogicalType.get(logicalTXProviderType).get(nodeId);
        CheckedFuture<Void, DTxException> deleteFuture = transaction.asyncDelete(logicalDatastoreType, instanceIdentifier);

        final SettableFuture<Void> retFuture = SettableFuture.create();

        Futures.addCallback(deleteFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
                retFuture.set(null);
            }

            @Override
            public void onFailure(Throwable throwable) {

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        CheckedFuture<Void, DTxException.RollbackFailedException> rollExcept = rollback();

                        Futures.addCallback(rollExcept, new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void aVoid) {
                                dtxReleaseDevices();
                                retFuture.setException(new DTxException.EditFailedException("Failed to delete but succeed to rollback"));
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                dtxReleaseDevices();
                                retFuture.setException(throwable);
                            }
                        });

                    }
                };
                new Thread(runnable).start();
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e = (Exception) e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("delete failed and rollback failure", e);
            }
        });
    }
}

