/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Interface for base communication with candidate or running NETCONF datastore, as defined in RFC 6241.
 * This service provides methods for manipulating and retrieving configuration and operational data on a NETCONF device.
 */
public interface DataStoreService {
    /**
     * Based on provided {@link LogicalDatastoreType} the {@code <get>} operation or {@code <get-config>} operation is
     * called. Retrieve device data from {@link YangInstanceIdentifier} path.
     *
     * @param store Define location of retrieved data {@link LogicalDatastoreType#OPERATIONAL}
     *              or {@link LogicalDatastoreType#CONFIGURATION}.
     * @param path {@link YangInstanceIdentifier} Path where requested data are located.
     * @param fields Specific fields that are read from device.
     * @return result of {@code <get>} or {@code <get-config>} operation.
     */
    ListenableFuture<Optional<NormalizedNode>> get(LogicalDatastoreType store, YangInstanceIdentifier path,
        List<YangInstanceIdentifier> fields);

    /**
     * The {@code <edit-config>} operation with {@code create} attribute. The configuration data identified by the
     * element containing this attribute is added to the configuration if and only if the configuration data does not
     * already exist in the configuration datastore.
     *
     * <p>If The {@code <lock>} operation is allowed and not already called, this operation will execute it.
     *
     * @param path {@link YangInstanceIdentifier} of provided data.
     * @param data {@link NormalizedNode} data.
     * @return result of {@code <edit-config>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> create(YangInstanceIdentifier path, NormalizedNode data);

    /**
     * The {@code <edit-config>} operation with {@code delete} attribute. The configuration data identified by the
     * element containing this attribute is deleted from the configuration  if the configuration data currently exists
     * in the configuration datastore.
     *
     * <p>If The {@code <lock>} operation is allowed and not already called, this operation will execute it.
     *
     * @param path {@link YangInstanceIdentifier} of provided data.
     * @return result of {@code <edit-config>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> delete(YangInstanceIdentifier path);

    /**
     * The {@code <edit-config>} operation with {@code remove} attribute. The configuration data identified by the
     * element containing this attribute is deleted from the configuration  if the configuration data currently exists
     * in the configuration datastore.
     *
     * <p>If The {@code <lock>} operation is allowed and not already called, this operation will execute it.
     *
     * @param path {@link YangInstanceIdentifier} of provided data.
     * @return result of {@code <edit-config>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> remove(YangInstanceIdentifier path);

    /**
     * The {@code <edit-config>} operation with {@code merge} attribute. The configuration data identified by the
     * element containing this attribute is added to the configuration if and only if the configuration data does not
     * already exist in the configuration datastore.
     *
     * <p>If The {@code <lock>} operation is allowed and not already called, this operation will execute it.
     *
     * @param path {@link YangInstanceIdentifier} of provided data.
     * @param data {@link NormalizedNode} data.
     * @return result of {@code <edit-config>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> merge(YangInstanceIdentifier path, NormalizedNode data);

    /**
     * The {@code <edit-config>} operation with {@code replace} attribute. The configuration data identified by the
     * element containing this attribute is added to the configuration if and only if the configuration data does not
     * already exist in the configuration datastore.
     *
     * <p>If The {@code <lock>} operation is allowed and not already called, this operation will execute it.
     *
     * @param path {@link YangInstanceIdentifier} of provided data.
     * @param data {@link NormalizedNode} data.
     * @return result of {@code <edit-config>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> replace(YangInstanceIdentifier path, NormalizedNode data);

    /**
     * Applies accumulated edit-config configuration changes. If device supports {@code :candidate} capability
     * and commit execution fails, a {@code <discard-changes>} operation will be automatically chained into the returned
     * ListenableFuture.
     *
     * <p>Release a configuration lock. Provided ListenableFuture waits until release of lock
     * operation is done.
     *
     * <p>Result of {@code <unlock>} operation or {@code <discard-changes>} if calls is only logged.
     *
     * @return result of {@code <commit>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> commit();

    /**
     * Executes the {@code <discard-changes>} operation to revert any uncommitted changes in the candidate datastore,
     * followed by the {@code <unlock>} operation to release any configuration lock held by this instance.
     *
     * <p>This method is primarily intended for exception unrelated to result of {@link ListenableFuture} provided by
     * this interface. This method is automatically wired to result of all ListenableFuture provided by this interface.
     *
     * @return result of {@code <discard-changes>} operation and {@code <unlock>} operation.
     */
    ListenableFuture<? extends DOMRpcResult> cancel();
}
