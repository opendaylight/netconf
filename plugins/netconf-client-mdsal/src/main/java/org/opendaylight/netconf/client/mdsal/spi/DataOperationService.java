/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Defines an SPI for asynchronously performing data manipulation and retrieval operations on NETCONF YANG datastore,
 * for configuration and operational state. This service is used to bridge ServerDataOperations and DataStoreService for
 * schema-aware devices.
 */
public interface DataOperationService {
    /**
     * Creates new data in the datastore at the specified path. Execute set of edit-config operation base on provided
     * {@link NormalizedNode} data to create requested resource. This operation is unsuccessful if data already exists
     * at the given path.
     *
     * @param path The {@link Data} representing the path where the new data should be created.
     * @param data The {@link NormalizedNode} containing the data to be created.
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the create operation.
     */
    ListenableFuture<? extends DOMRpcResult> createData(Data path, NormalizedNode data);

    /**
     * Deletes data from the datastore at the specified path. Execute set of edit-config operation base on provided
     * {@link Data} path to delete requested resource. This operation is unsuccessful if no data exists at the given
     * path.
     *
     * @param path The {@link Data} representing the path of the data to be deleted.
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the delete operation.
     */
    ListenableFuture<? extends DOMRpcResult> deleteData(Data path);

    /**
     * Removes data from the datastore at the specified path. Execute set of edit-config operation base on provided
     * {@link Data} path to remove requested resource exists. Operation succeeds even if the data does not exist.
     *
     * @param path The {@link Data} representing the path of the data to be removed.
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the remove operation.
     */
    ListenableFuture<? extends DOMRpcResult> removeData(Data path);

    /**
     * Merges the provided data with existing data in the datastore at the specified path. Execute set of edit-config
     * operation base on provided {@link Data} path to merge requested resource. Existing nodes at the path are updated
     * or overwritten by the new data, and new nodes are added.
     *
     * @param path The {@link Data} representing the base path for the merge operation.
     * @param data The {@link NormalizedNode} containing the data to be merged.
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the merge operation.
     */
    ListenableFuture<? extends DOMRpcResult> mergeData(Data path, NormalizedNode data);

    /**
     * Replaces existing data or creates new data in the datastore at the specified path. Execute set of edit-config
     * operation base on provided {@link Data} path to create requested resource. If data exists at the path, it is
     * entirely replaced by the new data. If no data exists, new data is created.
     *
     * @param path The {@link Data} representing the path where the data should be put (created or replaced).
     * @param data The {@link NormalizedNode} containing the data to be put.
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the put operation.
     */
    ListenableFuture<? extends DOMRpcResult> putData(Data path, NormalizedNode data);

    /**
     * Retrieves data from the datastore at the specified path. The retrieval can be customized using
     * {@link DataGetParams} to filter content (e.g., config, non-config, all), handle default values, and specify
     * fields or depth.
     *
     * @param path   The {@link Data} representing the path of the data to retrieve.
     * @param params The {@link DataGetParams} specifying additional parameters for the data retrieval.
     * @return A {@link ListenableFuture} that will complete with an {@link Optional} containing the
     *         {@link NormalizedNode} if data is found, or an empty {@link Optional} if not.
     */
    ListenableFuture<Optional<NormalizedNode>> getData(Data path, DataGetParams params);

    /**
     * Commits pending changes to a running datastore. This operation makes the changes persistent and visible to other
     * consumers.
     *
     * @return A {@link ListenableFuture} that will complete with a {@link DOMRpcResult} indicating the success or
     *         failure of the commit operation.
     */
    ListenableFuture<? extends DOMRpcResult> commit();
}
