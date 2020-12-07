/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dom.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Interface for base and additional operations for netconf (e.g. get, get-config, edit-config, (un)lock, commit etc).
 * &lt;edit-config&gt; operation is extended according it's attributes (merge, replace, create, delete, remove).
 * According to RFC-6241.
 */
public interface NetconfDataTreeService extends DOMService {

    /**
     * The &lt;lock&gt; operation.
     * Allows the client to lock the entire configuration datastore system of a device.
     *
     * @return result of &lt;lock&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> lock();

    /**
     * The &lt;unlock&gt; operation.
     * Used to release a configuration lock, previously obtained with the &lt;lock&gt; operation.
     */
    void unlock();

    /**
     * The &lt;discard-changes&gt; operation.
     * If device supports :candidate capability, discards any uncommitted changes by resetting
     * the candidate configuration with the content of the running configuration.
     */
    void discardChanges();

    /**
     * The &lt;get&gt; operation.
     * Retrieve running configuration and device state information.
     *
     * @return result of &lt;get&gt; operation
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> get(YangInstanceIdentifier path);

    /**
     * The &lt;get-config&gt; operation.
     * Retrieve all or part of a specified configuration datastore.
     *
     * @return result of &lt;get-config&gt; operation
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(YangInstanceIdentifier path);

    /**
     * The &lt;edit-config&gt; operation with "merge" attribute.
     * The configuration data identified by the element containing this attribute is merged with the configuration
     * at the corresponding level in the configuration datastore.
     *
     * @return result of &lt;edit-config&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> merge(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                   NormalizedNode<?, ?> data,
                                                   Optional<ModifyAction> defaultOperation);

    /**
     * The &lt;edit-config&gt; operation with "replace" attribute.
     * The configuration data identified by the element containing this attribute replaces any related configuration
     * in the configuration datastore.
     *
     * @return result of &lt;edit-config&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> replace(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                     NormalizedNode<?, ?> data,
                                                     Optional<ModifyAction> defaultOperation);

    /**
     * The &lt;edit-config&gt; operation with "create" attribute.
     * The configuration data identified by the element containing this attribute is added to the configuration if
     * and only if the configuration data does not already exist in the configuration datastore.
     *
     * @return result of &lt;edit-config&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> create(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                    NormalizedNode<?, ?> data,
                                                    Optional<ModifyAction> defaultOperation);

    /**
     * The &lt;edit-config&gt; operation with "create" attribute.
     * The configuration data identified by the element containing this attribute is deleted from the configuration
     * if and only if the configuration data currently exists in the configuration datastore.
     *
     * @return result of &lt;edit-config&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * The &lt;edit-config&gt; operation with "create" attribute.
     * The configuration data identified by the element containing this attribute is deleted from the configuration
     * if the configuration data currently exists in the configuration datastore.
     *
     * @return result of &lt;edit-config&gt; operation
     */
    ListenableFuture<? extends DOMRpcResult> remove(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * The &lt;commit&gt; operation.
     * If device supports :candidate capability, commit the candidate configuration as the device's
     * new current configuration.
     *
     * @return result of &lt;commit&gt; operation
     */
    ListenableFuture<? extends CommitInfo> commit(List<ListenableFuture<? extends DOMRpcResult>> resultsFutures);

    /**
     * Return device identifier.
     *
     * @return Device's identifier, must not be null.
     */
    @NonNull Object getDeviceId();
}