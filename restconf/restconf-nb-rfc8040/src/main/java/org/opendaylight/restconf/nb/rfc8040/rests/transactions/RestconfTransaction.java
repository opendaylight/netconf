/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.FluentFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * A handle to a set of operations being executed atomically on top of some backing store.
 */
// FIXME: it seems the first two operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
@Beta
public abstract class RestconfTransaction {
    RestconfTransaction() {
        // Hidden on purpose
    }

    /**
     * Rollback changes and unlock the datastore.
     */
    // FIXME: this looks synchronous, but it should not be
    public abstract void cancel();

    /**
     * Confirm previous operations.
     *
     * @return a FluentFuture containing the result of the commit information
     */
    public abstract FluentFuture<? extends @NonNull CommitInfo> commit();

    /**
     * Delete data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     */
    public abstract void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Remove data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path  the data object path
     */
    public abstract void remove(LogicalDatastoreType store, YangInstanceIdentifier path);

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
     * @param store         the logical data store which should be modified
     * @param path          the data object path
     * @param data          the data object to be merged to the specified path
     * @param schemaContext static view of compiled yang files
     */
    public abstract void create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data,
                                SchemaContext schemaContext);

    /**
     * Replace a piece of data at the specified path.
     *
     * @param store         the logical data store which should be modified
     * @param path          the data object path
     * @param data          the data object to be merged to the specified path
     * @param schemaContext static view of compiled yang files
     */
    public abstract void replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data,
                                 SchemaContext schemaContext);
}
