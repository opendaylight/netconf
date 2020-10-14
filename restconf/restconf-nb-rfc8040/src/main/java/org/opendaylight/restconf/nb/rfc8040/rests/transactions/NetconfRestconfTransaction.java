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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

final class NetconfRestconfTransaction extends RestconfTransaction {
    private final NetconfDataTreeService netconfService;

    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures;

    NetconfRestconfTransaction(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
        this.resultsFutures = netconfService.lock();
    }

    @Override
    public void cancel() {
        resultsFutures = null;
        netconfService.discardChanges();
        netconfService.unlock();
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        resultsFutures.add(netconfService.delete(store, path));
    }

    @Override
    public void remove(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        resultsFutures.add(netconfService.remove(store, path));
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        resultsFutures.add(netconfService.merge(store, path, data, Optional.empty()));
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
                resultsFutures.add(netconfService.create(store, childPath, child, Optional.empty()));
            }
        } else {
            resultsFutures.add(netconfService.create(store, path, data, Optional.empty()));
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
                resultsFutures.add(netconfService.replace(store, childPath, child, Optional.empty()));
            }
        } else {
            resultsFutures.add(netconfService.replace(store, path, data, Optional.empty()));
        }
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        return FluentFuture.from(netconfService.commit(resultsFutures));
    }
}
