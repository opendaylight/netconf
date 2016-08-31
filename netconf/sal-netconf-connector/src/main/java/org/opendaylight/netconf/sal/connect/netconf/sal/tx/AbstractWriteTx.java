/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MixinNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWriteTx implements DOMDataWriteTransaction {

    private static final Logger LOG  = LoggerFactory.getLogger(AbstractWriteTx.class);

    protected final RemoteDeviceId id;
    protected final NetconfBaseOps netOps;
    protected final boolean rollbackSupport;
    protected final List<ListenableFuture<DOMRpcResult>> resultsFutures;
    // Allow commit to be called only once
    protected boolean finished = false;

    public AbstractWriteTx(final NetconfBaseOps netOps, final RemoteDeviceId id, final boolean rollbackSupport) {
        this.netOps = netOps;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.resultsFutures = Lists.newArrayList();
        init();
    }

    protected static boolean isSuccess(final DOMRpcResult result) {
        return result.getErrors().isEmpty();
    }

    protected void checkNotFinished() {
        Preconditions.checkState(!isFinished(), "%s: Transaction %s already finished", id, getIdentifier());
    }

    protected boolean isFinished() {
        return finished;
    }

    @Override
    public synchronized boolean cancel() {
        if(isFinished()) {
            return false;
        }

        finished = true;
        cleanup();
        return true;
    }

    protected abstract void init();

    protected abstract void cleanup();

    @Override
    public Object getIdentifier() {
        return this;
    }

    @Override
    public synchronized void put(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        checkEditable(store);

        // trying to write only mixin nodes (not visible when serialized). Ignoring. Some devices cannot handle empty edit-config rpc
        if(containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring put for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.<NormalizedNode<?, ?>>fromNullable(data), Optional.of(ModifyAction.REPLACE), path);
        editConfig(path, Optional.fromNullable(data), editStructure, Optional.of(ModifyAction.NONE), "put");
    }

    @Override
    public synchronized void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        checkEditable(store);

        // trying to write only mixin nodes (not visible when serialized). Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring merge for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.<NormalizedNode<?, ?>>fromNullable(data), Optional.<ModifyAction>absent(), path);
        editConfig(path, Optional.fromNullable(data), editStructure, Optional.<ModifyAction>absent(), "merge");
    }

    /**
     * Check whether the data to be written consists only from mixins
     */
    private static boolean containsOnlyNonVisibleData(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        // There's only one such case:top level list (pathArguments == 1 && data is Mixin)
        // any other mixin nodes are contained by a "regular" node thus visible when serialized
        return path.getPathArguments().size() == 1 && data instanceof MixinNode;
    }

    @Override
    public synchronized void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        checkEditable(store);
        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.<NormalizedNode<?, ?>>absent(), Optional.of(ModifyAction.DELETE), path);
        editConfig(path, Optional.<NormalizedNode<?, ?>>absent(), editStructure, Optional.of(ModifyAction.NONE), "delete");
    }

    @Override
    public final ListenableFuture<RpcResult<TransactionStatus>> commit() {
        checkNotFinished();
        finished = true;

        return performCommit();
    }

    protected abstract ListenableFuture<RpcResult<TransactionStatus>> performCommit();

    private void checkEditable(final LogicalDatastoreType store) {
        checkNotFinished();
        Preconditions.checkArgument(store == LogicalDatastoreType.CONFIGURATION, "Can edit only configuration data, not %s", store);
    }

    protected abstract void editConfig(final YangInstanceIdentifier path, final Optional<NormalizedNode<?, ?>> data, final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> defaultOperation, final String operation);

    protected ListenableFuture<RpcResult<TransactionStatus>> resultsToTxStatus() {
        final SettableFuture<RpcResult<TransactionStatus>> transformed = SettableFuture.create();

        Futures.addCallback(Futures.allAsList(resultsFutures), new FutureCallback<List<DOMRpcResult>>() {
            @Override
            public void onSuccess(final List<DOMRpcResult> domRpcResults) {
                domRpcResults.forEach(domRpcResult -> {
                    if(!domRpcResult.getErrors().isEmpty() && !transformed.isDone()) {
                        NetconfDocumentedException exception =
                                new NetconfDocumentedException(id + ":RPC during tx failed",
                                        DocumentedException.ErrorType.application,
                                        DocumentedException.ErrorTag.operation_failed,
                                        DocumentedException.ErrorSeverity.error);
                        transformed.setException(exception);
                    }
                });

                if(!transformed.isDone()) {
                    transformed.set(RpcResultBuilder.success(TransactionStatus.COMMITED).build());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                // TODO should we wrap throwable in NetconfDocumentedException
                transformed.setException(throwable);
            }
        });

        return transformed;
    }
}
