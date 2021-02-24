/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfRestconfTransaction extends RestconfTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfRestconfTransaction.class);

    private final NetconfDataTreeService netconfService;
    private final ListenableFuture<? extends DOMRpcResult> lockResult;
    private volatile ListenableFuture<? extends DOMRpcResult> lastOperationFuture;
    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();

    NetconfRestconfTransaction(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
        this.lockResult = netconfService.lock();
        this.lastOperationFuture = null;
    }

    @Override
    public void cancel() {
        resultsFutures = null;
        netconfService.discardChanges();
        netconfService.unlock();
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        scheduleOperation(() -> netconfService.delete(store, path));
    }

    @Override
    public void remove(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        scheduleOperation(() -> netconfService.remove(store, path));
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        scheduleOperation(() -> netconfService.merge(store, path, data, Optional.empty()));
    }

    @Override
    public void create(final LogicalDatastoreType store, final YangInstanceIdentifier path,
           final NormalizedNode<?, ?> data, final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubTree.getIdentifier()),
                emptySubTree);

            for (final NormalizedNode<?, ?> child : ((NormalizedNodeContainer<?, ?, ?>) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                scheduleOperation(() -> netconfService.create(store, childPath, child, Optional.empty()));
            }
        } else {
            scheduleOperation(() -> netconfService.create(store, path, data, Optional.empty()));
        }
    }

    @Override
    public void replace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data, final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubTree.getIdentifier()),
                emptySubTree);

            for (final NormalizedNode<?, ?> child : ((NormalizedNodeContainer<?, ?, ?>) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                scheduleOperation(() -> netconfService.replace(store, childPath, child, Optional.empty()));
            }
        } else {
            scheduleOperation(() -> netconfService.replace(store, path, data, Optional.empty()));
        }
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        return FluentFuture.from(netconfService.commit(resultsFutures));
    }

    private void scheduleOperation(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        final ListenableFuture<? extends DOMRpcResult> operationFuture;
        // if that's a first operation in transaction, then start chain from the result of lock operation,
        // otherwise use last operation in transaction
        if (lastOperationFuture == null) {
            operationFuture = Futures.transformAsync(lockResult,
                future -> {
                    if (future.getErrors().isEmpty() || allWarnings(future.getErrors())) {
                        return Futures.immediateFuture(new DefaultDOMRpcResult());
                    } else {
                        return Futures.immediateFailedFuture(new RuntimeException("Lock operation failed"));
                    }
                },
                MoreExecutors.directExecutor());
        } else {
            operationFuture = Futures.transformAsync(lastOperationFuture, future -> operation.get(),
                MoreExecutors.directExecutor());
        }

        // save to results container, as we might be interested in results for all operations later
        resultsFutures.add(operationFuture);

        // set latest operation future to chain upcoming futures
        lastOperationFuture = operationFuture;
    }

    private static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == RpcError.ErrorSeverity.WARNING);
    }
}
