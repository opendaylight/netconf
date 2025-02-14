/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static com.google.common.base.Verify.verifyNotNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes.fromInstanceId;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.mdsal.spi.data.ExistenceCheck.Conflict;
import org.opendaylight.restconf.mdsal.spi.data.ExistenceCheck.Result;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DistinctNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MdsalRestconfTransaction extends RestconfTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfTransaction.class);

    private DOMDataTreeReadWriteTransaction rwTx;

    MdsalRestconfTransaction(final DatabindContext databind, final DOMDataBroker dataBroker) {
        super(databind);
        rwTx = dataBroker.newReadWriteTransaction();
    }

    @Override
    public void cancel() {
        if (rwTx != null) {
            rwTx.cancel();
            rwTx = null;
        }
    }

    @Override
    protected void deleteImpl(final YangInstanceIdentifier path) throws RequestException {
        if (RestconfStrategy.syncAccess(verifyNotNull(rwTx).exists(CONFIGURATION, path), path)) {
            rwTx.delete(CONFIGURATION, path);
        } else {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Data does not exist",
                new ErrorPath(databind, path));
        }
    }

    @Override
    protected void removeImpl(final YangInstanceIdentifier path) {
        verifyNotNull(rwTx).delete(CONFIGURATION, path);
    }

    @Override
    protected void mergeImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        verifyNotNull(rwTx).merge(CONFIGURATION, path, data);
    }

    @Override
    protected void createImpl(final YangInstanceIdentifier path, final NormalizedNode data) throws RequestException {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final var emptySubTree = fromInstanceId(databind.modelContext(), path);
            merge(YangInstanceIdentifier.of(emptySubTree.name()), emptySubTree);
            ensureParentsByMerge(path);

            final var children = ((DistinctNodeContainer<?, ?>) data).body();

            // Fire off an existence check
            final var check = ExistenceCheck.start(databind, verifyNotNull(rwTx), CONFIGURATION, path, false, children);

            // ... and perform any put() operations, which happen-after existence check
            for (var child : children) {
                final var childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }

            // ... finally collect existence checks and abort the transaction if any of them failed.
            if (getOrThrow(check) instanceof Conflict conflict) {
                throw new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                    new ErrorPath(databind, conflict.path()));
            }
        } else {
            RestconfStrategy.checkItemDoesNotExists(databind, verifyNotNull(rwTx).exists(CONFIGURATION, path), path);
            ensureParentsByMerge(path);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    private static @NonNull Result getOrThrow(final Future<Result> future) throws RequestException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestException("Interrupted while waiting", e);
        } catch (ExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), RequestException.class);
            throw new RequestException("Operation failed", e);
        }
    }


    @Override
    protected void replaceImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final var emptySubtree = fromInstanceId(databind.modelContext(), path);
            merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
            ensureParentsByMerge(path);

            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }
        } else {
            ensureParentsByMerge(path);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    /**
     * Merge parents of data.
     *
     * @param path    path of data
     */
    // FIXME: this method should only be invoked if we are crossing an implicit list.
    @Override
    void ensureParentsByMerge(final YangInstanceIdentifier path) {
        final var parent = path.getParent();
        if (parent != null && !parent.isEmpty()) {
            final var rootNormalizedPath = path.getAncestor(1);
            merge(rootNormalizedPath, fromInstanceId(databind.modelContext(), parent));
        }
    }

    @Override
    public ListenableFuture<? extends @NonNull CommitInfo> commit() {
        final var ret = verifyNotNull(rwTx).commit();
        rwTx = null;
        return ret;
    }

    @Override
    protected ListenableFuture<Optional<NormalizedNode>> read(final YangInstanceIdentifier path) {
        return verifyNotNull(rwTx).read(CONFIGURATION, path);
    }

    @Override
    protected NormalizedNodeContainer<?> readList(final YangInstanceIdentifier path) throws RequestException {
        return (NormalizedNodeContainer<?>) RestconfStrategy.syncAccess(read(path), path).orElse(null);
    }
}
