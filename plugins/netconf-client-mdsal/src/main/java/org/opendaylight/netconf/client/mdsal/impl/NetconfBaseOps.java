/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.COMMIT_RPC_CONTENT;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.DISCARD_CHANGES_RPC_CONTENT;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.EDIT_CONTENT_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.GET_RPC_CONTENT;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DEFAULT_OPERATION_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_ERROR_OPTION_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_LOCK_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_SOURCE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_TARGET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_UNLOCK_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_VALIDATE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toFilterStructure;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.input.source.ConfigSource;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * Provides base operations for NETCONF e.g. {@code get}, {@code get-config}, {@code edit-config}, {@code commit} etc.
 * as per <a href="https://www.rfc-editor.org/rfc/rfc6241#section-7">RFC6241 Protocol Operations</a>.
 */
// FIXME: turn Optional arguments to @Nullable
public final class NetconfBaseOps {
    private static final NodeIdentifier CONFIG_SOURCE_NODEID = NodeIdentifier.create(ConfigSource.QNAME);
    private static final NodeIdentifier CONFIG_TARGET_NODEID = NodeIdentifier.create(ConfigTarget.QNAME);
    private static final LeafNode<String> NETCONF_ERROR_OPTION_ROLLBACK =
        ImmutableNodes.leafNode(NETCONF_ERROR_OPTION_NODEID, "rollback-on-error");

    private final NetconfRpcService rpc;
    private final MountPointContext mountContext;
    private final RpcStructureTransformer transformer;

    public NetconfBaseOps(final Rpcs rpc, final MountPointContext mountContext) {
        this.rpc = requireNonNull(rpc);
        this.mountContext = requireNonNull(mountContext);

        if (rpc instanceof Rpcs.Schemaless) {
            transformer = new SchemalessRpcStructureTransformer();
        } else if (rpc instanceof Rpcs.Normalized) {
            transformer = new NetconfRpcStructureTransformer(mountContext);
        } else {
            throw new IllegalStateException("Unhandled rpcs " + rpc);
        }
    }

    public ListenableFuture<? extends DOMRpcResult> lock(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier datastore) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_LOCK_QNAME, getLockContent(datastore)));
    }

    private static <T> ListenableFuture<T> addCallback(final FutureCallback<? super T> callback,
            final ListenableFuture<T> future) {
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> lockCandidate(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_LOCK_QNAME,
            getLockContent(NETCONF_CANDIDATE_NODEID)));
    }

    public ListenableFuture<? extends DOMRpcResult> lockRunning(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_LOCK_QNAME,
            getLockContent(NETCONF_RUNNING_NODEID)));
    }

    public ListenableFuture<? extends DOMRpcResult> unlock(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier datastore) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_UNLOCK_QNAME,
            getUnLockContent(datastore)));
    }

    public ListenableFuture<? extends DOMRpcResult> unlockRunning(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_UNLOCK_QNAME,
            getUnLockContent(NETCONF_RUNNING_NODEID)));
    }

    public ListenableFuture<? extends DOMRpcResult> unlockCandidate(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_UNLOCK_QNAME,
            getUnLockContent(NETCONF_CANDIDATE_NODEID)));
    }

    public ListenableFuture<? extends DOMRpcResult> discardChanges(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_DISCARD_CHANGES_QNAME,
            DISCARD_CHANGES_RPC_CONTENT));
    }

    public ListenableFuture<? extends DOMRpcResult> commit(final FutureCallback<DOMRpcResult> callback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_COMMIT_QNAME, COMMIT_RPC_CONTENT));
    }

    public ListenableFuture<? extends DOMRpcResult> validate(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier datastore) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_VALIDATE_QNAME,
            getValidateContent(requireNonNull(datastore))));
    }

    public ListenableFuture<? extends DOMRpcResult> validateCandidate(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NETCONF_CANDIDATE_NODEID);
    }

    public ListenableFuture<? extends DOMRpcResult> validateRunning(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NETCONF_RUNNING_NODEID);
    }

    public ListenableFuture<? extends DOMRpcResult> copyConfig(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier sourceDatastore, final NodeIdentifier targetDatastore) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_COPY_CONFIG_QNAME,
            getCopyConfigContent(sourceDatastore, targetDatastore)));
    }

    public ListenableFuture<? extends DOMRpcResult> copyRunningToCandidate(
            final FutureCallback<DOMRpcResult> callback) {
        return copyConfig(callback, NETCONF_RUNNING_NODEID, NETCONF_CANDIDATE_NODEID);
    }

    public ListenableFuture<? extends DOMRpcResult> getConfig(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier datastore, final Optional<YangInstanceIdentifier> filterPath) {
        final var source = getSourceNode(datastore);
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_GET_CONFIG_QNAME,
            nonEmptyFilter(filterPath)
                .map(path -> NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source,
                    transformer.toFilterStructure(path)))
                .orElseGet(() -> NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source))));
    }

    private ListenableFuture<? extends DOMRpcResult> getConfig(final FutureCallback<DOMRpcResult> callback,
            final NodeIdentifier datastore, final Optional<YangInstanceIdentifier> filterPath,
            final List<YangInstanceIdentifier> fields) {
        final ContainerNode rpcInput;
        if (nonEmptyFilter(filterPath).isPresent()) {
            rpcInput = NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, getSourceNode(datastore),
                transformer.toFilterStructure(List.of(FieldsFilter.of(filterPath.orElseThrow(), fields))));
        } else if (containsEmptyPath(fields)) {
            rpcInput = NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, getSourceNode(datastore));
        } else {
            rpcInput = NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID,
                    getSourceNode(datastore), getSubtreeFilterFromRootFields(fields));
        }
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_GET_CONFIG_QNAME, rpcInput));
    }

    /**
     * Calling GET-CONFIG RPC with subtree filter that is specified by {@link YangInstanceIdentifier}.
     *
     * @param callback   RPC response callback
     * @param filterPath path to requested data
     * @return asynchronous completion token with read {@link NormalizedNode} wrapped in {@link Optional} instance
     */
    public ListenableFuture<Optional<NormalizedNode>> getConfigRunningData(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return extractData(filterPath, getConfigRunning(callback, filterPath));
    }

    /**
     * Calling GET-CONFIG RPC with subtree filter tha tis specified by parent {@link YangInstanceIdentifier} and list
     * of specific fields that caller would like to read. Field paths are relative to parent path.
     *
     * @param callback   RPC response callback
     * @param filterPath parent path to requested data
     * @param fields     paths to specific fields that are selected under parent path
     * @return asynchronous completion token with read {@link NormalizedNode} wrapped in {@link Optional} instance
     */
    public ListenableFuture<Optional<NormalizedNode>> getConfigRunningData(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath, final List<YangInstanceIdentifier> fields) {
        if (fields.isEmpty()) {
            // RFC doesn't allow to build subtree filter that would expect just empty element in response
            return Futures.immediateFailedFuture(new IllegalArgumentException(
                "Failed to build NETCONF GET-CONFIG RPC: provided list of fields is empty; filter path: "
                    + filterPath));
        }
        return extractData(filterPath, getConfigRunning(callback, filterPath, fields));
    }

    /**
     * Calling GET RPC with subtree filter that is specified by {@link YangInstanceIdentifier}.
     *
     * @param callback   RPC response callback
     * @param filterPath path to requested data
     * @return asynchronous completion token with read {@link NormalizedNode} wrapped in {@link Optional} instance
     */
    public ListenableFuture<Optional<NormalizedNode>> getData(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return extractData(filterPath, get(callback, filterPath));
    }

    /**
     * Calling GET RPC with subtree filter tha tis specified by parent {@link YangInstanceIdentifier} and list
     * of specific fields that caller would like to read. Field paths are relative to parent path.
     *
     * @param callback   RPC response callback
     * @param filterPath parent path to requested data
     * @param fields     paths to specific fields that are selected under parent path
     * @return asynchronous completion token with read {@link NormalizedNode} wrapped in {@link Optional} instance
     */
    public ListenableFuture<Optional<NormalizedNode>> getData(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath, final List<YangInstanceIdentifier> fields) {
        if (fields.isEmpty()) {
            // RFC doesn't allow to build subtree filter that would expect just empty element in response
            return Futures.immediateFailedFuture(new IllegalArgumentException(
                    "Failed to build NETCONF GET RPC: provided list of fields is empty; filter path: " + filterPath));
        }
        return extractData(filterPath, get(callback, filterPath, fields));
    }

    private ListenableFuture<Optional<NormalizedNode>> extractData(final Optional<YangInstanceIdentifier> path,
            final ListenableFuture<? extends DOMRpcResult> configRunning) {
        return Futures.transform(configRunning, result -> {
            final var errors = result.errors();
            checkArgument(errors.isEmpty(), "Unable to read data: %s, errors: %s", path, errors);
            return transformer.selectFromDataStructure(result.value()
                .getChildByArg(NetconfMessageTransformUtil.NETCONF_DATA_NODEID), path.orElseThrow());
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<? extends DOMRpcResult> getConfigRunning(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NETCONF_RUNNING_NODEID, filterPath);
    }

    private ListenableFuture<? extends DOMRpcResult> getConfigRunning(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath, final List<YangInstanceIdentifier> fields) {
        return getConfig(callback, NETCONF_RUNNING_NODEID, filterPath, fields);
    }

    public ListenableFuture<? extends DOMRpcResult> getConfigCandidate(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NETCONF_CANDIDATE_NODEID, filterPath);
    }

    public ListenableFuture<? extends DOMRpcResult> get(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_GET_QNAME,
            nonEmptyFilter(filterPath)
                .map(path -> NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID,
                    toFilterStructure(path, mountContext.getEffectiveModelContext())))
                .orElse(NetconfMessageTransformUtil.GET_RPC_CONTENT)));
    }

    private ListenableFuture<? extends DOMRpcResult> get(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath, final List<YangInstanceIdentifier> fields) {
        final ContainerNode rpcInput;
        if (nonEmptyFilter(filterPath).isPresent()) {
            rpcInput = NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, transformer.toFilterStructure(
                    Collections.singletonList(FieldsFilter.of(filterPath.orElseThrow(), fields))));
        } else if (containsEmptyPath(fields)) {
            rpcInput = GET_RPC_CONTENT;
        } else {
            rpcInput = NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, getSubtreeFilterFromRootFields(fields));
        }
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_GET_QNAME, rpcInput));
    }

    private static boolean containsEmptyPath(final List<YangInstanceIdentifier> fields) {
        return fields.stream().anyMatch(YangInstanceIdentifier::isEmpty);
    }

    private DataContainerChild getSubtreeFilterFromRootFields(final List<YangInstanceIdentifier> fields) {
        return transformer.toFilterStructure(fields.stream()
            .map(fieldPath -> Map.entry(
                YangInstanceIdentifier.of(Iterables.limit(fieldPath.getPathArguments(), 1)),
                YangInstanceIdentifier.of(Iterables.skip(fieldPath.getPathArguments(), 1))))
            .collect(Collectors.groupingBy(Entry::getKey,
                Collectors.mapping(Entry::getValue, Collectors.toUnmodifiableList())))
            .entrySet().stream()
            .map(entry -> FieldsFilter.of(entry.getKey(), entry.getValue()))
            .collect(Collectors.toUnmodifiableList()));
    }

    private static Optional<YangInstanceIdentifier> nonEmptyFilter(final Optional<YangInstanceIdentifier> filterPath) {
        return filterPath.filter(path -> !path.isEmpty());
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigCandidate(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild editStructure,
            final EffectiveOperation modifyAction, final boolean rollback) {
        return editConfig(callback, NETCONF_CANDIDATE_NODEID, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigCandidate(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild editStructure,
            final boolean rollback) {
        return editConfig(callback, NETCONF_CANDIDATE_NODEID, editStructure, Optional.empty(), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigRunning(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild editStructure,
            final EffectiveOperation modifyAction, final boolean rollback) {
        return editConfig(callback, NETCONF_RUNNING_NODEID, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigRunning(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild editStructure,
            final boolean rollback) {
        return editConfig(callback, NETCONF_RUNNING_NODEID, editStructure, Optional.empty(), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(
            final FutureCallback<? super DOMRpcResult> callback, final NodeIdentifier datastore,
            final DataContainerChild editStructure, final Optional<EffectiveOperation> modifyAction,
            final boolean rollback) {
        return addCallback(requireNonNull(callback), rpc.invokeNetconf(NETCONF_EDIT_CONFIG_QNAME,
            getEditConfigContent(requireNonNull(datastore), requireNonNull(editStructure), modifyAction, rollback)));
    }

    public ChoiceNode createEditConfigStructure(final Optional<NormalizedNode> lastChild,
            final Optional<EffectiveOperation> operation, final YangInstanceIdentifier dataPath) {
        return Builders.choiceBuilder()
            .withNodeIdentifier(EDIT_CONTENT_NODEID)
            .withChild(transformer.createEditConfigStructure(lastChild, dataPath, operation))
            .build();
    }

    private static ContainerNode getEditConfigContent(final NodeIdentifier datastore,
            final DataContainerChild editStructure, final Optional<EffectiveOperation> defaultOperation,
            final boolean rollback) {
        final var editBuilder = Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_EDIT_CONFIG_NODEID)
            // Target
            .withChild(getTargetNode(datastore));

        // Default operation
        defaultOperation.ifPresent(op -> {
            editBuilder.withChild(ImmutableNodes.leafNode(NETCONF_DEFAULT_OPERATION_NODEID, op.xmlValue()));
        });

        // Error option
        if (rollback) {
            editBuilder.withChild(NETCONF_ERROR_OPTION_ROLLBACK);
        }

        // Edit content
        return editBuilder.withChild(editStructure).build();
    }

    public static @NonNull ContainerNode getSourceNode(final NodeIdentifier datastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_SOURCE_NODEID)
            .withChild(Builders.choiceBuilder()
                .withNodeIdentifier(CONFIG_SOURCE_NODEID)
                .withChild(ImmutableNodes.leafNode(datastore, Empty.value()))
                .build())
            .build();
    }

    public static @NonNull ContainerNode getLockContent(final NodeIdentifier datastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_LOCK_NODEID)
            .withChild(getTargetNode(datastore))
            .build();
    }

    public static @NonNull ContainerNode getTargetNode(final NodeIdentifier datastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_TARGET_NODEID)
            .withChild(Builders.choiceBuilder()
                .withNodeIdentifier(CONFIG_TARGET_NODEID)
                .withChild(ImmutableNodes.leafNode(datastore, Empty.value()))
                .build())
            .build();
    }

    public static @NonNull ContainerNode getCopyConfigContent(final NodeIdentifier sourceDatastore,
            final NodeIdentifier targetDatastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_COPY_CONFIG_NODEID)
            .withChild(getTargetNode(targetDatastore))
            .withChild(getSourceNode(sourceDatastore))
            .build();
    }

    public static @NonNull ContainerNode getValidateContent(final NodeIdentifier sourceDatastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_VALIDATE_NODEID)
            .withChild(getSourceNode(sourceDatastore))
            .build();
    }

    public static @NonNull ContainerNode getUnLockContent(final NodeIdentifier datastore) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_UNLOCK_NODEID)
            .withChild(getTargetNode(datastore))
            .build();
    }
}
