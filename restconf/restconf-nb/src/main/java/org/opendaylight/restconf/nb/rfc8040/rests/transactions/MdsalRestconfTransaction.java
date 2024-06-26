/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static com.google.common.base.Verify.verifyNotNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes.fromInstanceId;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.ExistenceCheck.Conflict;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ServerErrorPath;
import org.opendaylight.restconf.server.api.ServerException;
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
    void deleteImpl(final YangInstanceIdentifier path) throws ServerException {
        if (DefaultRestconfStrategy.syncAccess(verifyNotNull(rwTx).exists(CONFIGURATION, path), path)) {
            rwTx.delete(CONFIGURATION, path);
        } else {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, new ServerErrorPath(databind, path),
                "Data does not exist");
        }
    }

    @Override
    void removeImpl(final YangInstanceIdentifier path) {
        verifyNotNull(rwTx).delete(CONFIGURATION, path);
    }

    @Override
    void mergeImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        verifyNotNull(rwTx).merge(CONFIGURATION, path, data);
    }

    @Override
    void createImpl(final YangInstanceIdentifier path, final NormalizedNode data) throws ServerException {
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
            if (check.getOrThrow() instanceof Conflict conflict) {
                throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                    new ServerErrorPath(databind, conflict.path()), "Data already exists");
            }
        } else {
            DefaultRestconfStrategy.checkItemDoesNotExists(databind, verifyNotNull(rwTx).exists(CONFIGURATION, path),
                path);
            ensureParentsByMerge(path);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    @Override
    void replaceImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
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
        final var normalizedPathWithoutChildArgs = new ArrayList<YangInstanceIdentifier.PathArgument>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final var it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final var pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.of(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        merge(rootNormalizedPath,
            fromInstanceId(databind.modelContext(), YangInstanceIdentifier.of(normalizedPathWithoutChildArgs)));
    }

    @Override
    public ListenableFuture<? extends @NonNull CommitInfo> commit() {
        final var ret = verifyNotNull(rwTx).commit();
        rwTx = null;
        return ret;
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final YangInstanceIdentifier path) {
        return verifyNotNull(rwTx).read(CONFIGURATION, path);
    }

    @Override
    NormalizedNodeContainer<?> readList(final YangInstanceIdentifier path) throws ServerException {
        return (NormalizedNodeContainer<?>) DefaultRestconfStrategy.syncAccess(read(path), path).orElse(null);
    }
}
