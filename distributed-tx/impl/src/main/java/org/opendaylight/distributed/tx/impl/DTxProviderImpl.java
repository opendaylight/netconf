/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.distributed.tx.spi.TransactionLock;
import org.opendaylight.distributed.tx.spi.TxProvider;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DTxProviderImpl implements DTxProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DTxProviderImpl.class);
    private final Map<Object, DtxReleaseWrapper> currentTxs = Maps.newHashMap();
    private final Map<DTXLogicalTXProviderType, TxProvider> txProviderMap;
    private final TransactionLock dtxLock;

    public DTxProviderImpl(@Nonnull final Map<DTXLogicalTXProviderType, TxProvider> txProviders){
        txProviderMap = txProviders;
        dtxLock = new DTxTransactionLockImpl(txProviderMap);
    }

    @Nonnull @Override public synchronized DTx newTx(@Nonnull final Set<InstanceIdentifier<?>> nodes)
        throws DTxException.DTxInitializationFailedException {
        boolean lockSucceed = dtxLock.lockDevices(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodes);

        if(!lockSucceed) {
            throw new DTxException.DTxInitializationFailedException("Failed to lock devices");
        }

        Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> m = new HashMap<>();
        m.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, nodes);
        final DtxReleaseWrapper dtxReleaseWrapper = new DtxReleaseWrapper(new DtxImpl(txProviderMap.get(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER), nodes, dtxLock), m);
        currentTxs.put(dtxReleaseWrapper.getIdentifier(), dtxReleaseWrapper);

        return dtxReleaseWrapper;
    }

    @Nonnull
    @Override
    public synchronized DTx newTx(@Nonnull Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap) throws DTxException.DTxInitializationFailedException {
        boolean lockSucceed = this.dtxLock.lockDevices(nodesMap);

        if(!lockSucceed) {
            throw new DTxException.DTxInitializationFailedException("Failed to lock devices");
        }

        for(DTXLogicalTXProviderType type : nodesMap.keySet()){
            Preconditions.checkArgument(this.txProviderMap.containsKey(type), "Unknown node: %d. Not in transaction", type);
        }

        final DtxReleaseWrapper dtxReleaseWrapper = new DtxReleaseWrapper(new DtxImpl(txProviderMap, nodesMap, dtxLock), nodesMap);
        currentTxs.put(dtxReleaseWrapper.getIdentifier(), dtxReleaseWrapper);
        return dtxReleaseWrapper;
    }

    @Override public void close() throws Exception {
        for (Map.Entry<Object, DtxReleaseWrapper> outstandingTx : currentTxs.entrySet()) {
            LOG.warn("Cancelling outstanding distributed transaction: {}", outstandingTx.getKey());
            outstandingTx.getValue().cancel();
        }
    }

    private final class DtxReleaseWrapper implements DTx {

        private final DTx delegate;
        private Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap;

        private DtxReleaseWrapper(final DTx delegate, final Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodes) {
            this.delegate = delegate;
            this.nodesMap = nodes;
        }

        private void releaseNodes() {
            synchronized (DTxProviderImpl.this) {
                Preconditions.checkNotNull(currentTxs.remove(getIdentifier()), "Unable to cleanup distributed transaction");
                dtxLock.releaseDevices(nodesMap);
            }
        }

        @Deprecated
        @Override public boolean cancel() throws DTxException.RollbackFailedException {
            final boolean cancel = delegate.cancel();
            releaseNodes();
            return cancel;
        }

        @Override
        public <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException {
            return delegate.mergeAndRollbackOnFailure(logicalDatastoreType, instanceIdentifier, t, nodeId);
        }

        @Override
        public <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException {
            CheckedFuture<Void, DTxException> putFuture = delegate.putAndRollbackOnFailure(logicalDatastoreType, instanceIdentifier, t, nodeId);

            return putFuture;
        }

        @Override
        public CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier, InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException, DTxException.RollbackFailedException {
            return delegate.deleteAndRollbackOnFailure(logicalDatastoreType, instanceIdentifier, nodeId);
        }

        @Override public void delete(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier) throws DTxException.EditFailedException {
            delegate.delete(logicalDatastoreType, instanceIdentifier);
        }

        @Override public void delete(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier, final InstanceIdentifier<?> nodeId)
            throws DTxException.EditFailedException, DTxException.RollbackFailedException {
            delegate.delete(logicalDatastoreType, instanceIdentifier, nodeId);
        }

        @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t)
            throws DTxException.EditFailedException, DTxException.RollbackFailedException {
            delegate.merge(logicalDatastoreType, instanceIdentifier, t);
        }

        @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b)
            throws DTxException.EditFailedException {
            delegate.merge(logicalDatastoreType, instanceIdentifier, t, b);
        }

        @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b,
            final InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException {
            delegate.merge(logicalDatastoreType, instanceIdentifier, t, b, nodeId);
        }

        @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId)
            throws DTxException.EditFailedException {
            delegate.merge(logicalDatastoreType, instanceIdentifier, t, nodeId);
        }

        @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t) throws DTxException.EditFailedException {
            delegate.put(logicalDatastoreType, instanceIdentifier, t);
        }

        @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b)
            throws DTxException.EditFailedException {
            delegate.put(logicalDatastoreType, instanceIdentifier, t, b);
        }

        @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b,
            final InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException {
            delegate.put(logicalDatastoreType, instanceIdentifier, t, b, nodeId);
        }

        @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId)
            throws DTxException.EditFailedException {
            delegate.put(logicalDatastoreType, instanceIdentifier, t, nodeId);
        }

        @Override public CheckedFuture<Void, TransactionCommitFailedException> submit()
            throws DTxException.SubmitFailedException, DTxException.RollbackFailedException {
            final CheckedFuture<Void, TransactionCommitFailedException> submit = delegate.submit();

            Futures.addCallback(submit, new FutureCallback<Void>() {
                @Override public void onSuccess(final Void result) {
                    releaseNodes();
                }

                @Override public void onFailure(final Throwable t) {
                    releaseNodes();
                }
            });
            return submit;
        }

        @Deprecated
        @Override public ListenableFuture<RpcResult<TransactionStatus>> commit() {
            throw new UnsupportedOperationException("Deprecated");
        }

        @Override public Object getIdentifier() {
            return delegate.getIdentifier();
        }
        @Override
        public CheckedFuture<Void, DTxException.RollbackFailedException> rollback(){return delegate.rollback();}

        @Override
        public <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
                DTXLogicalTXProviderType logicalTXProviderType, LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) {
            return delegate.mergeAndRollbackOnFailure(logicalTXProviderType, logicalDatastoreType, instanceIdentifier, t, nodeId);
        }

        @Override
        public <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(DTXLogicalTXProviderType logicalTXProviderType, LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) {
            return delegate.putAndRollbackOnFailure(logicalTXProviderType, logicalDatastoreType, instanceIdentifier, t, nodeId);
        }

        @Override
        public CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(DTXLogicalTXProviderType logicalTXProviderType, LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier, InstanceIdentifier<?> nodeId) {
            return delegate.deleteAndRollbackOnFailure(logicalTXProviderType, logicalDatastoreType, instanceIdentifier, nodeId);
        }
    }
}

