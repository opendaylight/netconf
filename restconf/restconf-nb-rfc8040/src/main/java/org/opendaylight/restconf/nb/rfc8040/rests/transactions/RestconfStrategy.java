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
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * This interface allows interact with netconf operations in different ways.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
public interface RestconfStrategy {

    /**
     * Lock the entire datastore.
     */
    void prepareReadWriteExecution();

    /**
     * Rollback changes and unlock the datastore.
     */
    void cancel();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Read data selected using fields from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the parent data object path
     * @param fields paths to selected fields relative to parent path
     * @return a ListenableFuture containing the result of the read
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store,
            YangInstanceIdentifier path, List<YangInstanceIdentifier> fields);

    /**
     * Check if data already exists in the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a FluentFuture containing the result of the check
     */
    FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Delete data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     */
    void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Merges a piece of data with the existing data at a specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Stores a piece of data at the specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    void create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Replace a piece of data at the specified path.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     */
    void replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    /**
     * Confirm previous operations.
     *
     * @return a FluentFuture containing the result of the commit information
     */
    FluentFuture<? extends @NonNull CommitInfo> commit();

    /**
     * Get transaction chain for creating specific transaction for specific operation.
     *
     * @return transaction chain or null
     */
    @Nullable DOMTransactionChain getTransactionChain();

    /**
     * Get instance identifier of data.
     *
     * @return {@link InstanceIdentifierContext}
     */
    InstanceIdentifierContext<?> getInstanceIdentifier();

    /**
     * Get transaction chain handler for creating new transaction chain.
     *
     * @return {@link TransactionChainHandler} or null
     */
    @Nullable TransactionChainHandler getTransactionChainHandler();

    /**
     * Create a new and same type strategy for communication with netconf interface with
     * a new {@link InstanceIdentifierContext}.
     *
     * @return {@link RestconfStrategy}
     */
    RestconfStrategy buildStrategy(InstanceIdentifierContext<?> instanceIdentifierContext);
}
