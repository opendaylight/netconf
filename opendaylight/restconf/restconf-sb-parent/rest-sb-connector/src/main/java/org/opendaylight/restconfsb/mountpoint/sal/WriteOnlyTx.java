/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconfsb.mountpoint.sal;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.util.RestconfUtil;
import org.opendaylight.restconfsb.mountpoint.sal.changes.Change;
import org.opendaylight.restconfsb.mountpoint.sal.changes.ChangeFactory;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;

class WriteOnlyTx implements DOMDataWriteTransaction {
    private final RestconfFacade facade;
    private final List<Change> changes;
    private final ChangeFactory changeFactory;
    private boolean finished = false;


    WriteOnlyTx(final RestconfFacade facade) {
        this.facade = facade;
        changes = new ArrayList<>();
        changeFactory = new ChangeFactory(facade);
    }

    @Override
    public synchronized void put(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier,
                                 final NormalizedNode<?, ?> normalizedNode) {
        checkState(logicalDatastoreType);
        addChange(yangInstanceIdentifier, normalizedNode, ChangeFactory.Type.PUT);
    }

    @Override
    public synchronized void merge(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier,
                                   final NormalizedNode<?, ?> normalizedNode) {
        checkState(logicalDatastoreType);
        addChange(yangInstanceIdentifier, normalizedNode, ChangeFactory.Type.MERGE);
    }

    @Override
    public synchronized void delete(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier) {
        checkState(logicalDatastoreType);
        addChange(yangInstanceIdentifier, null, ChangeFactory.Type.DELETE);
    }

    @Override
    public synchronized boolean cancel() {
        if (finished) {
            return false;
        }
        finished = true;
        return true;
    }

    @Override
    public synchronized CheckedFuture<Void, TransactionCommitFailedException> submit() {
        Preconditions.checkState(!finished, "Transaction %s already finished", getIdentifier());
        final Iterator<Change> iterator = changes.iterator();
        ListenableFuture<Void> chain = null;
        if (iterator.hasNext()) {
            chain = iterator.next().apply(null);
            while (iterator.hasNext()) {
                chain = Futures.transform(chain, iterator.next());
            }
        }
        finished = true;
        if (chain != null) {
            return Futures.makeChecked(chain, new Function<Exception, TransactionCommitFailedException>() {
                @Nullable
                @Override
                public TransactionCommitFailedException apply(@Nullable final Exception input) {
                    if (input.getCause() instanceof HttpException) {
                        final HttpException exception = (HttpException) input.getCause();
                        final Collection<RpcError> rpcErrors = facade.parseErrors(exception);
                        return new TransactionCommitFailedException("Write failed", exception, RestconfUtil.toRpcErrorArray(rpcErrors));
                    } else {
                        return new TransactionCommitFailedException("Write failed", input);
                    }
                }
            });
        } else {
            return Futures.immediateCheckedFuture(null);
        }

    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        throw new UnsupportedOperationException("this function is deprecated use submit instead");
    }

    @Override
    public Object getIdentifier() {
        return this;
    }

    /**
     * Checks datastore argument and state of transaction
     */
    private void checkState(final LogicalDatastoreType datastore) {
        Preconditions.checkState(datastore == LogicalDatastoreType.CONFIGURATION);
        Preconditions.checkState(!finished, "Transaction was already submitted");
    }

    private void addChange(final YangInstanceIdentifier yangInstanceIdentifier, final NormalizedNode<?, ?> normalizedNode,
                           final ChangeFactory.Type type) {
        if (normalizedNode instanceof MapNode) {
            for (final MapEntryNode mapEntryNode : ((MapNode) normalizedNode).getValue()) {
                final YangInstanceIdentifier yangBuilder = YangInstanceIdentifier.builder(yangInstanceIdentifier)
                        .node(mapEntryNode.getIdentifier())
                        .build();
                changes.add(changeFactory.create(type, yangBuilder, mapEntryNode));

            }
        } else if (normalizedNode instanceof UnkeyedListNode) {
            for (final UnkeyedListEntryNode unkeyedListEntryNode : ((UnkeyedListNode) normalizedNode).getValue()) {
                final YangInstanceIdentifier yangBuilder = YangInstanceIdentifier.builder(yangInstanceIdentifier)
                        .node(unkeyedListEntryNode.getIdentifier())
                        .build();
                changes.add(changeFactory.create(type, yangBuilder, unkeyedListEntryNode));
            }
        } else {
            changes.add(changeFactory.create(type, yangInstanceIdentifier, normalizedNode));
        }
    }
}

