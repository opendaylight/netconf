/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface RestconfStrategy extends DatabindAware {
    /**
     * Lock the entire datastore.
     *
     * @return A {@link RestconfTransaction}. This transaction needs to be either committed or canceled before doing
     *         anything else.
     */
    RestconfTransaction prepareWriteExecution();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Check if data already exists in the configuration datastore.
     *
     * @param path the data object path
     */
    // FIXME: this method should be hosted in RestconfTransaction
    // FIXME: this method should only be needed in MdsalRestconfStrategy
    ListenableFuture<Boolean> exists(YangInstanceIdentifier path);

    void dataGET(ServerRequest<DataGetResult> request, Data path, DataGetParams params);

    @NonNullByDefault
    void delete(ServerRequest<Empty> request, YangInstanceIdentifier path);
}
