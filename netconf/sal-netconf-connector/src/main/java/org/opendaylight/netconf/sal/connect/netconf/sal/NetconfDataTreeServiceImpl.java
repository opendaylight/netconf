/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDataTreeServiceImpl implements NetconfDataTreeService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataTreeServiceImpl.class);

    private final RemoteDeviceId id;
    private final NetconfBaseOps netconfOps;
    private final boolean rollbackSupport;
    private final boolean candidateSupported;
    private final boolean runningWritable;

    public NetconfDataTreeServiceImpl(final RemoteDeviceId id, final MountPointContext mountContext,
                                      final DOMRpcService rpc,
                                      final NetconfSessionPreferences netconfSessionPreferences) {
        this.id = id;
        this.netconfOps = new NetconfBaseOps(rpc, mountContext);
        // get specific attributes from netconf preferences and get rid of it
        // no need to keep the entire preferences object, its quite big with all the capability QNames
        candidateSupported = netconfSessionPreferences.isCandidateSupported();
        runningWritable = netconfSessionPreferences.isRunningWritable();
        rollbackSupport = netconfSessionPreferences.isRollbackSupported();
        Preconditions.checkArgument(candidateSupported || runningWritable,
                "Device %s has advertised neither :writable-running nor :candidate capability."
                        + "At least one of these should be advertised. Failed to establish a session.", id.getName());
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> getConfig(final LogicalDatastoreType store,
                                                                  final YangInstanceIdentifier path) {
        switch (store) {
            case CONFIGURATION:
                return getConfigConfiguration(path);
            case OPERATIONAL:
                return getConfigOperational(path);
            default:
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                        "%s, Cannot read data %s for %s datastore, unknown datastore type", id, path, store));
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return getConfig(store, path).transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
                                                          final YangInstanceIdentifier path,
                                                          final NormalizedNode<?, ?> data,
                                                          final Optional<ModifyAction> defaultOperation) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.MERGE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
                                                            final YangInstanceIdentifier path,
                                                            final NormalizedNode<?, ?> data,
                                                            final Optional<ModifyAction> defaultOperation) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.REPLACE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
                                                           final YangInstanceIdentifier path,
                                                           final NormalizedNode<?, ?> data,
                                                           final Optional<ModifyAction> defaultOperation) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.CREATE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
                                                           final YangInstanceIdentifier path,
                                                           final NormalizedNode<?, ?> data,
                                                           final Optional<ModifyAction> defaultOperation) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.DELETE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
                                                           final YangInstanceIdentifier path,
                                                           final NormalizedNode<?, ?> data,
                                                           final Optional<ModifyAction> defaultOperation) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.REMOVE), path);

        return editConfig(defaultOperation, editStructure);
    }

    private ListenableFuture<? extends DOMRpcResult> editConfig(final Optional<ModifyAction> defaultOperation,
                                                                final DataContainerChild<?, ?> editStructure) {
        if (candidateSupported) {
            if (runningWritable) {
                return editConfigCandidate(defaultOperation, editStructure);
            } else {
                return editConfigCandidate(defaultOperation, editStructure);
            }
        } else {
            return editConfigRunning(defaultOperation, editStructure);
        }
    }

    private ListenableFuture<? extends DOMRpcResult> editConfigRunning(final Optional<ModifyAction> defaultOperation,
                                                                       final DataContainerChild<?, ?> editStructure) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit running", id);
        if (defaultOperation.isPresent()) {
            return netconfOps.editConfigRunning(callback, editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            return netconfOps.editConfigRunning(callback, editStructure, rollbackSupport);
        }
    }

    private ListenableFuture<? extends DOMRpcResult> editConfigCandidate(final Optional<ModifyAction> defaultOperation,
                                                                         final DataContainerChild<?, ?> editStructure) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
        if (defaultOperation.isPresent()) {
            return netconfOps.editConfigCandidate(callback, editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            return netconfOps.editConfigCandidate(callback, editStructure, rollbackSupport);
        }
    }

    private FluentFuture<Optional<NormalizedNode<?, ?>>> getConfigConfiguration(final YangInstanceIdentifier path) {
        return remapException(netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path)));
    }

    private FluentFuture<Optional<NormalizedNode<?, ?>>> getConfigOperational(final YangInstanceIdentifier path) {
        return remapException(netconfOps.getData(
                new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path)));
    }

    private static <T> FluentFuture<T> remapException(final ListenableFuture<T> input) {
        final SettableFuture<T> ret = SettableFuture.create();
        Futures.addCallback(input, new FutureCallback<T>() {

            @Override
            public void onSuccess(final T result) {
                ret.set(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(cause instanceof ReadFailedException ? cause
                        : new ReadFailedException("NETCONF operation failed", cause));
            }
        }, MoreExecutors.directExecutor());
        return FluentFuture.from(ret);
    }
}
