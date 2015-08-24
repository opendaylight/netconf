/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.input.source.ConfigSource;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Provides base operations for netconf e.g. get, get-config, edit-config, (un)lock, commit etc.
 * According to RFC-6241
 */
public final class NetconfBaseOps {

    private final DOMRpcService rpc;
    private final SchemaContext schemaContext;

    public NetconfBaseOps(final DOMRpcService rpc, final SchemaContext schemaContext) {
        this.rpc = rpc;
        this.schemaContext = schemaContext;
    }

    public ListenableFuture<DOMRpcResult> lock(final FutureCallback<DOMRpcResult> callback, final QName datastore) {
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(datastore);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), getLockContent(datastore));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> lockCandidate(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), getLockContent(NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME));
        Futures.addCallback(future, callback);
        return future;
    }


    public ListenableFuture<DOMRpcResult> lockRunning(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), getLockContent(NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> unlock(final FutureCallback<DOMRpcResult> callback, final QName datastore) {
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(datastore);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), getUnLockContent(datastore));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> unlockRunning(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), getUnLockContent(NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> unlockCandidate(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), getUnLockContent(NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> discardChanges(final FutureCallback<DOMRpcResult> callback) {
        Preconditions.checkNotNull(callback);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME), null);
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> commit(final FutureCallback<DOMRpcResult> callback) {
        Preconditions.checkNotNull(callback);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME), NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> validate(final FutureCallback<DOMRpcResult> callback, final QName datastore) {
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(datastore);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME), getValidateContent(datastore));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> validateCandidate(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
    }


    public ListenableFuture<DOMRpcResult> validateRunning(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME);
    }

    public ListenableFuture<DOMRpcResult> copyConfig(final FutureCallback<DOMRpcResult> callback, final QName source, final QName target) {
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(target);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME), getCopyConfigContent(source, target));
        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> copyRunningToCandidate(final FutureCallback<DOMRpcResult> callback) {
        return copyConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
    }

    public ListenableFuture<DOMRpcResult> getConfig(final FutureCallback<DOMRpcResult> callback, final QName datastore, final Optional<YangInstanceIdentifier> filterPath) {
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(datastore);

        final ListenableFuture<DOMRpcResult> future;
        if (isFilterPresent(filterPath)) {
            // FIXME the source node has to be wrapped in a choice
            final DataContainerChild<?, ?> node = NetconfMessageTransformUtil.toFilterStructure(filterPath.get(), schemaContext);
            future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME),
                            NetconfMessageTransformUtil.wrap(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME, getSourceNode(datastore), node));
        } else {
            future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME),
                            NetconfMessageTransformUtil.wrap(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME, getSourceNode(datastore)));
        }

        Futures.addCallback(future, callback);
        return future;
    }

    public ListenableFuture<DOMRpcResult> getConfigRunning(final FutureCallback<DOMRpcResult> callback, final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME, filterPath);
    }

    public ListenableFuture<DOMRpcResult> getConfigCandidate(final FutureCallback<DOMRpcResult> callback, final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME, filterPath);
    }

    public ListenableFuture<DOMRpcResult> get(final FutureCallback<DOMRpcResult> callback, final Optional<YangInstanceIdentifier> filterPath) {
        Preconditions.checkNotNull(callback);

        final ListenableFuture<DOMRpcResult> future;

        future = isFilterPresent(filterPath) ?
                rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_GET_QNAME), NetconfMessageTransformUtil.wrap(NetconfMessageTransformUtil.NETCONF_GET_QNAME, NetconfMessageTransformUtil.toFilterStructure(filterPath.get(), schemaContext))) :
                rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_GET_QNAME), NetconfMessageTransformUtil.GET_RPC_CONTENT);

        Futures.addCallback(future, callback);
        return future;
    }

    private boolean isFilterPresent(final Optional<YangInstanceIdentifier> filterPath) {
        return filterPath.isPresent() && !filterPath.get().isEmpty();
    }

    public ListenableFuture<DOMRpcResult> editConfigCandidate(final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure, final ModifyAction modifyAction, final boolean rollback) {
        return editConfig(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<DOMRpcResult> editConfigCandidate(final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure, final boolean rollback) {
        return editConfig(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME, editStructure, Optional.<ModifyAction>absent(), rollback);
    }

    public ListenableFuture<DOMRpcResult> editConfigRunning(final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure, final ModifyAction modifyAction, final boolean rollback) {
        return editConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<DOMRpcResult> editConfigRunning(final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure, final boolean rollback) {
        return editConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME, editStructure, Optional.<ModifyAction>absent(), rollback);
    }

    public ListenableFuture<DOMRpcResult> editConfig(final FutureCallback<? super DOMRpcResult> callback, final QName datastore, final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> modifyAction, final boolean rollback) {
        Preconditions.checkNotNull(editStructure);
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(datastore);

        final ListenableFuture<DOMRpcResult> future = rpc.invokeRpc(NetconfMessageTransformUtil.toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), getEditConfigContent(datastore, editStructure, modifyAction, rollback));

        Futures.addCallback(future, callback);
        return future;
    }

    public DataContainerChild<?, ?> createEditConfigStrcture(final Optional<NormalizedNode<?, ?>> lastChild, final Optional<ModifyAction> operation, final YangInstanceIdentifier dataPath) {
        return NetconfMessageTransformUtil.createEditConfigStructure(schemaContext, dataPath, operation, lastChild);
    }

    private ContainerNode getEditConfigContent(final QName datastore, final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> defaultOperation, final boolean rollback) {
        final DataContainerNodeAttrBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> editBuilder = Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME));

        // Target
        editBuilder.withChild(getTargetNode(datastore));

        // Default operation
        if(defaultOperation.isPresent()) {
            final String opString = defaultOperation.get().name().toLowerCase();
            editBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_DEFAULT_OPERATION_QNAME)).withValue(opString).build());
        }

        // Error option
        if(rollback) {
            editBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_ERROR_OPTION_QNAME)).withValue(NetconfMessageTransformUtil.ROLLBACK_ON_ERROR_OPTION).build());
        }

        // Edit content
        editBuilder.withChild(editStructure);
        return editBuilder.build();
    }

    public static DataContainerChild<?, ?> getSourceNode(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_SOURCE_QNAME))
                .withChild(
                        Builders.choiceBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(ConfigSource.QNAME)).withChild(
                                Builders.leafBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(datastore)).build()).build()
                ).build();
    }

    public static ContainerNode getLockContent(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME))
                .withChild(getTargetNode(datastore)).build();
    }

    public static DataContainerChild<?, ?> getTargetNode(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_TARGET_QNAME))
                .withChild(
                        Builders.choiceBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(ConfigTarget.QNAME)).withChild(
                                Builders.leafBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(datastore)).build()).build()
                ).build();
    }

    public static NormalizedNode<?, ?> getCopyConfigContent(final QName source, final QName target) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME))
                .withChild(getTargetNode(target)).withChild(getSourceNode(source)).build();
    }

    public static NormalizedNode<?, ?> getValidateContent(final QName source) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME))
                .withChild(getSourceNode(source)).build();
    }

    public static NormalizedNode<?, ?> getUnLockContent(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NetconfMessageTransformUtil.toId(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME))
                .withChild(getTargetNode(datastore)).build();
    }

}
