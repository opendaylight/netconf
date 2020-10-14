/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Baseline execution strategy for various RESTCONF operations.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
// FIXME: it seems the first three operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
public abstract class RestconfStrategy {
    RestconfStrategy() {
        // Hidden on purpose
    }

    /**
     * Look up the appropriate strategy for a particular mount point.
     *
     * @param mountPoint Target mount point
     * @return A strategy, or null if the mount point does not expose a supported interface
     * @throws NullPointerException if {@code mountPoint} is null
     */
    public static Optional<RestconfStrategy> forMountPoint(final DOMMountPoint mountPoint) {
        final Optional<RestconfStrategy> netconf = mountPoint.getService(NetconfDataTreeService.class)
            .map(NetconfRestconfStrategy::new);
        if (netconf.isPresent()) {
            return netconf;
        }

        return mountPoint.getService(DOMDataBroker.class).map(MdsalRestconfStrategy::new);
    }

    /**
     * Lock the entire datastore.
     */
    public abstract void prepareReadWriteExecution();

    /**
     * Confirm previous operations.
     *
     * @return a FluentFuture containing the result of the commit information
     */
    public abstract FluentFuture<? extends @NonNull CommitInfo> commit();

    /**
     * Rollback changes and unlock the datastore.
     */
    public abstract void cancel();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    public abstract ListenableFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store,
        YangInstanceIdentifier path);

    /**
     * Check if data already exists in the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a FluentFuture containing the result of the check
     */
    public abstract FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Delete data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     */
    public abstract void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Merges a piece of data with the existing data at a specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    public abstract void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Stores a piece of data at the specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    public abstract void create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Replace a piece of data at the specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    public abstract void replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Get transaction chain for creating specific transaction for specific operation.
     *
     * @return transaction chain or null
     */
    // FIXME: these look like an implementation detail
    public abstract @Nullable DOMTransactionChain getTransactionChain();

    /**
     * Get transaction chain handler for creating new transaction chain.
     *
     * @return {@link TransactionChainHandler} or null
     */
    // FIXME: these look like an implementation detail
    public abstract @Nullable TransactionChainHandler getTransactionChainHandler();
}
