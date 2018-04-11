/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public final class NetconfDataBrokerAdapter implements NetconfDataBroker {

    private final DOMDataBroker delegate;

    public NetconfDataBrokerAdapter(final DOMDataBroker dataBroker) {
        this.delegate = checkNotNull(dataBroker, "dataBroker should not be null");
    }

    @Override
    public NetconfReadOnlyTransaction newReadOnlyTransaction() {
        return new NetconfReadOnlyTransactionAdapter(delegate.newReadOnlyTransaction());
    }

    @Override
    public NetconfWriteTransaction newWriteOnlyTransaction() {
        return new NetconfWriteTransactionAdapter(delegate.newWriteOnlyTransaction());
    }

    @Override
    public NetconfReadWriteTransaction newReadWriteTransaction() {
        return new NetconfReadWriteTransactionAdapter(delegate.newReadWriteTransaction());
    }

    @Override
    public NetconfTransactionChain createTransactionChain(final TransactionChainListener listener) {
        return new NetconfTransactionChainAdapter(delegate.createTransactionChain(listener));
    }

    @Override
    public ListenerRegistration<DOMDataChangeListener> registerDataChangeListener(final LogicalDatastoreType store,
                                                                                  final YangInstanceIdentifier path,
                                                                                  final DOMDataChangeListener listener,
                                                                                  final DataChangeScope triggeringScope
    ) {
        return delegate.registerDataChangeListener(store, path, listener, triggeringScope);
    }

    @Nonnull
    @Override
    public Map<Class<? extends DOMDataBrokerExtension>, DOMDataBrokerExtension> getSupportedExtensions() {
        return delegate.getSupportedExtensions();
    }

    private interface NetconfReadTransactionAdapter extends NetconfReadTransaction {

        DOMDataReadTransaction getDelegate();

        @Override
        default CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
            return getDelegate().read(store, path);
        }

        @Override
        default CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
                                                                   final YangInstanceIdentifier path) {
            return getDelegate().exists(store, path);
        }

        @Override
        default Object getIdentifier() {
            return getDelegate().getIdentifier();
        }
    }

    private static final class NetconfReadOnlyTransactionAdapter
        implements NetconfReadOnlyTransaction, NetconfReadTransactionAdapter {

        private final DOMDataReadOnlyTransaction delegate;

        private NetconfReadOnlyTransactionAdapter(final DOMDataReadOnlyTransaction readTx) {
            this.delegate = readTx;
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public DOMDataReadTransaction getDelegate() {
            return delegate;
        }
    }

    @SuppressWarnings("checkstyle:FinalClass")
    private static class NetconfWriteTransactionAdapter implements NetconfWriteTransaction {

        private final DOMDataWriteTransaction delegate;

        private NetconfWriteTransactionAdapter(final DOMDataWriteTransaction writeTx) {
            this.delegate = writeTx;
        }

        DOMDataWriteTransaction getWriteDelegate() {
            return delegate;
        }

        @Override
        public CheckedFuture<Void, DataValidationFailedException> validate() {
            return Futures.immediateFailedCheckedFuture(
                new DataValidationFailedException(
                    new UnsupportedOperationException(("Validate capability is not supported"))));
        }

        @Override
        public boolean cancel() {
            return delegate.cancel();
        }

        @Override
        public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
            delegate.delete(store, path);
        }

        @Override
        public CheckedFuture<Void, TransactionCommitFailedException> submit() {
            return delegate.submit();
        }

        @Override
        public ListenableFuture<RpcResult<TransactionStatus>> commit() {
            return delegate.commit();
        }

        @Override
        public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                        final NormalizedNode<?, ?> data) {
            delegate.put(store, path, data);
        }

        @Override
        public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                          final NormalizedNode<?, ?> data) {
            delegate.merge(store, path, data);
        }

        @Override
        public Object getIdentifier() {
            return delegate.getIdentifier();
        }
    }

    private static final class NetconfReadWriteTransactionAdapter extends NetconfWriteTransactionAdapter
        implements NetconfReadTransactionAdapter, NetconfReadWriteTransaction {

        private NetconfReadWriteTransactionAdapter(final DOMDataReadWriteTransaction readTx) {
            super(readTx);
        }

        @Override
        public DOMDataReadTransaction getDelegate() {
            return (DOMDataReadTransaction) super.getWriteDelegate();
        }
    }

    private static final class NetconfTransactionChainAdapter implements NetconfTransactionChain {
        private final DOMTransactionChain delegate;

        NetconfTransactionChainAdapter(final DOMTransactionChain txChain) {
            this.delegate = txChain;
        }

        @Override
        public DOMDataReadOnlyTransaction newReadOnlyTransaction() {
            return delegate.newReadOnlyTransaction();
        }

        @Override
        public DOMDataReadWriteTransaction newReadWriteTransaction() {
            return delegate.newReadWriteTransaction();
        }

        @Override
        public DOMDataWriteTransaction newWriteOnlyTransaction() {
            return delegate.newWriteOnlyTransaction();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
