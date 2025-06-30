/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dom.api;

import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Interface for base and additional operations for NETCONF (e.g. {@code get}, {@code get-config}, {@code edit-config},
 * {@code lock}, {@code unlock}, {@code commit}, etc).
 * The {@code <edit-config>} operation is extended according its attributes (merge, replace, create, delete, remove), as
 * per RFC6241.
 */
public interface NetconfDataTreeService extends DOMService<NetconfDataTreeService, NetconfDataTreeService.Extension> {
    /**
     * Type capture of a {@link DOMService.Extension} applicable to {@link NetconfDataTreeService} implementations.
     */
    interface Extension extends DOMService.Extension<NetconfDataTreeService, Extension> {
        // Marker interface
    }

    /**
     * Return device identifier.
     *
     * @return Device's identifier, must not be {@code null}.
     */
    @NonNull Object getDeviceId();

    /**
     * The {@code <lock>} operation. Allows the client to lock the entire configuration datastore system of a device.
     *
     * @return result of {@code <lock>} operation
     */
    @CheckReturnValue
    ListenableFuture<? extends DOMRpcResult> lock();

    /**
     * The {@code <lock>} operation. Used to release a configuration lock, previously obtained with the {@code <lock>}
     * operation.
     *
     * @return result of {@code <unlock>} operation
     */
    @CheckReturnValue
    ListenableFuture<? extends DOMRpcResult> unlock();

    /**
     * The {@code <discard-changes>} operation. If device supports {@code :candidate} capability, discards any
     * uncommitted changes by resetting the candidate configuration with the content of the running configuration.
     *
     * @return result of {@code <discard-changes>} operation
     */
    ListenableFuture<? extends DOMRpcResult> discardChanges();

    /**
     * The {@code <get>} operation. Retrieve running configuration and device state information.
     *
     * @return result of {@code <get>} operation
     */
    ListenableFuture<Optional<NormalizedNode>> get(YangInstanceIdentifier path);

    /**
     * The {@code <get>} operation with specific fields that are read from device.
     *
     * @param path   path to data
     * @param fields list of fields (paths relative to parent path)
     * @return result of {@code <get>} operation
     */
    ListenableFuture<Optional<NormalizedNode>> get(YangInstanceIdentifier path, List<YangInstanceIdentifier> fields);

    /**
     * The {@code <get-config>} operation. Retrieve all or part of a specified configuration datastore.
     *
     * @return result of {@code <get-config>} operation
     */
    ListenableFuture<Optional<NormalizedNode>> getConfig(YangInstanceIdentifier path);

    /**
     * The {@code <get-config>} operation with specified fields that are read from device.
     *
     * @return result of {@code <get-config>} operation
     */
    ListenableFuture<Optional<NormalizedNode>> getConfig(YangInstanceIdentifier path,
        List<YangInstanceIdentifier> fields);

    /**
     * The {@code <edit-config>} operation with {@code merge} attribute. The configuration data identified by the
     * element containing this attribute is merged with the configuration at the corresponding level in the
     * configuration datastore.
     *
     * @return result of {@code <edit-config>} operation
     */
    ListenableFuture<? extends DOMRpcResult> merge(LogicalDatastoreType store, YangInstanceIdentifier path,
        NormalizedNode data, Optional<EffectiveOperation> defaultOperation);

    /**
     * The {@code <edit-config>} operation with {@code replace} attribute. The configuration data identified by the
     * element containing this attribute replaces any related configuration in the configuration datastore.
     *
     * @return result of {@code <edit-config>} operation
     */
    ListenableFuture<? extends DOMRpcResult> replace(LogicalDatastoreType store, YangInstanceIdentifier path,
        NormalizedNode data, Optional<EffectiveOperation> defaultOperation);

    /**
     * The {@code <edit-config>} operation with {@code create} attribute. The configuration data identified by the
     * element containing this attribute is added to the configuration if and only if the configuration data does not
     * already exist in the configuration datastore.
     *
     * @return result of{@code <edit-config>} operation
     */
    ListenableFuture<? extends DOMRpcResult> create(LogicalDatastoreType store, YangInstanceIdentifier path,
        NormalizedNode data, Optional<EffectiveOperation> defaultOperation);

    /**
     * The {@code <edit-config>} operation with {@code create} attribute. The configuration data identified by the
     * element containing this attribute is deleted from the configuration if and only if the configuration data
     * currently exists in the configuration datastore.
     *
     * @return result of {@code <edit-config>} operation
     */
    ListenableFuture<? extends DOMRpcResult> delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * The {@code <edit-config>} operation with {@code create} attribute. The configuration data identified by the
     * element containing this attribute is deleted from the configuration if the configuration data currently exists
     * in the configuration datastore.
     *
     * @return result of {@code <edit-config>} operation
     */
    ListenableFuture<? extends DOMRpcResult> remove(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * The {@code <commit>} operation. If device supports {@code :candidate} capability, commit the candidate
     * configuration as the device's new current configuration.
     *
     * @return result of {@code <commit>} operation
     */
    ListenableFuture<? extends DOMRpcResult> commit();
}
