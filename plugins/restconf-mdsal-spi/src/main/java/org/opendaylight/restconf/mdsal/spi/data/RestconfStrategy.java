/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes.fromInstanceId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerDataOperations;
import org.opendaylight.restconf.server.spi.ApiPathCanonizer;
import org.opendaylight.restconf.server.spi.Insert;
import org.opendaylight.restconf.server.spi.NormalizedFormattableBody;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.with.defaults.rev110601.WithDefaultsMode;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline class for {@link ServerDataOperations} implementations.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
// FIXME: it seems the first three operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
public abstract class RestconfStrategy extends AbstractServerDataOperations {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStrategy.class);
    private static final @NonNull DataPutResult PUT_CREATED = new DataPutResult(true);
    private static final @NonNull DataPutResult PUT_REPLACED = new DataPutResult(false);
    private static final @NonNull DataPatchResult PATCH_EMPTY = new DataPatchResult();

    protected final @NonNull DatabindContext databind;

    RestconfStrategy(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    /**
     * Lock the entire datastore.
     *
     * @return A {@link RestconfTransaction}. This transaction needs to be either committed or canceled before doing
     *         anything else.
     */
    abstract RestconfTransaction prepareWriteExecution();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Check if data already exists in the configuration datastore.
     *
     * @param request {@link ServerRequest} for this request
     * @param path the data object path
     */
    // FIXME: this method should be hosted in RestconfTransaction
    // FIXME: this method should only be needed in MdsalRestconfStrategy
    abstract ListenableFuture<Boolean> exists(YangInstanceIdentifier path);

    @Override
    public final void mergeData(final ServerRequest<DataPatchResult> request, final Data path,
            final NormalizedNode data) {
        final var instance = path.instance();
        final var tx = prepareWriteExecution();
        // FIXME: this method should be further specialized to eliminate this call -- it is only needed for MD-SAL
        tx.ensureParentsByMerge(instance);
        tx.merge(instance, data);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                // TODO: extract details once CommitInfo can communicate them
                request.completeWith(PATCH_EMPTY);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public final void putData(final ServerRequest<DataPutResult> request, final Data path, final NormalizedNode data) {
        final var instance = path.instance();
        final Boolean exists;
        try {
            exists = syncAccess(exists(instance), instance);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        completePutData(request, path, exists, replaceAndCommit(prepareWriteExecution(), instance, data));
    }

    @Override
    public final void putData(final ServerRequest<DataPutResult> request, final Data path, final Insert insert,
            final NormalizedNode data) {
        final var instance = path.instance();
        final Boolean exists;
        try {
            exists = syncAccess(exists(instance), instance);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        final ListenableFuture<? extends CommitInfo> commitFuture;
        final var parentPath = instance.coerceParent();
        try {
            checkListAndOrderedType(parentPath);
            commitFuture = insertAndCommitPut(instance, data, insert, parentPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        completePutData(request, path, exists, commitFuture);
    }

    private void completePutData(final ServerRequest<DataPutResult> request, final Data path,
            final boolean exists, final ListenableFuture<? extends CommitInfo> future) {
        Futures.addCallback(future, new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<? extends CommitInfo> insertAndCommitPut(final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull Insert insert, final YangInstanceIdentifier parentPath)
                throws RequestException {
        final var tx = prepareWriteExecution();

        return switch (insert.insert()) {
            case FIRST -> {
                try {
                    final var readData = tx.readList(parentPath);
                    if (readData == null || readData.isEmpty()) {
                        yield replaceAndCommit(tx, path, data);
                    }
                    tx.remove(parentPath);
                    tx.replace(path, data);
                    tx.replace(parentPath, readData);
                } catch (RequestException e) {
                    throw e;
                }
                yield tx.commit();
            }
            case LAST -> replaceAndCommit(tx, path, data);
            case BEFORE -> {
                try {
                    final var readData = tx.readList(parentPath);
                    if (readData == null || readData.isEmpty()) {
                        yield replaceAndCommit(tx, path, data);
                    }
                    insertWithPointPut(tx, path, data, verifyNotNull(insert.pointArg()), readData, true);
                } catch (RequestException e) {
                    throw e;
                }
                yield tx.commit();
            }
            case AFTER -> {
                try {
                    final var readData = tx.readList(parentPath);
                    if (readData == null || readData.isEmpty()) {
                        yield replaceAndCommit(tx, path, data);
                    }
                    insertWithPointPut(tx, path, data, verifyNotNull(insert.pointArg()), readData, false);
                } catch (RequestException e) {
                    throw e;
                }
                yield tx.commit();
            }
        };
    }

    private void insertWithPointPut(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) throws RequestException {
        tx.remove(path.getParent());

        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }

        int lastInsertedPosition = 0;
        final var emptySubtree = fromInstanceId(databind.modelContext(), path.getParent());
        tx.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
            final var childPath = path.coerceParent().node(nodeChild.name());
            tx.replace(childPath, nodeChild);
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
        }
    }

    private static ListenableFuture<? extends CommitInfo> replaceAndCommit(final RestconfTransaction tx,
            final YangInstanceIdentifier path, final NormalizedNode data) {
        tx.replace(path, data);
        return tx.commit();
    }

    private DataSchemaNode checkListAndOrderedType(final YangInstanceIdentifier path) throws RequestException {
        // FIXME: we have this available in InstanceIdentifierContext
        final var dataSchemaNode = databind.schemaTree().findChild(path).orElseThrow().dataSchemaNode();

        final String message;
        if (dataSchemaNode instanceof ListSchemaNode listSchema) {
            if (listSchema.isUserOrdered()) {
                return listSchema;
            }
            message = "Insert parameter can be used only with ordered-by user list.";
        } else if (dataSchemaNode instanceof LeafListSchemaNode leafListSchema) {
            if (leafListSchema.isUserOrdered()) {
                return leafListSchema;
            }
            message = "Insert parameter can be used only with ordered-by user leaf-list.";
        } else {
            message = "Insert parameter can be used only with list or leaf-list";
        }
        throw new RequestException(ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT, message);
    }

    @Override
    protected final void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        final var tx = prepareWriteExecution();
        try {
            tx.create(path.instance(), data);
        } catch (RequestException e) {
            tx.cancel();
            request.completeWith(e);
            return;
        }
        completeCreateData(request, path, data, tx.commit());
    }

    @Override
    protected final void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final Insert insert, final NormalizedNode data) {
        final ListenableFuture<? extends CommitInfo> future;
        try {
            checkListAndOrderedType(path.instance());
            future = insertAndCommit(path.instance(), data, insert);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        completeCreateData(request, path, data, future);
    }

    private void completeCreateData(final ServerRequest<? super CreateResourceResult> request,
            final Data path, final NormalizedNode data,
            final ListenableFuture<? extends CommitInfo> future) {
        Futures.addCallback(future, new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                final ApiPath apiPath;
                try {
                    apiPath = new ApiPathCanonizer(databind).dataToApiPath(
                        data instanceof MapNode mapData && !mapData.isEmpty()
                        ? path.instance().node(mapData.body().iterator().next().name()) : path.instance());
                } catch (RequestException e) {
                    // This should never happen
                    request.completeWith(e);
                    return;
                }
                request.completeWith(new CreateResourceResult(apiPath));
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "POST", path));
            }

        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<? extends CommitInfo> insertAndCommit(final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull Insert insert) throws RequestException {
        final var tx = prepareWriteExecution();

        return switch (insert.insert()) {
            case FIRST -> {
                try {
                    final var readData = tx.readList(path);
                    if (readData == null || readData.isEmpty()) {
                        tx.replace(path, data);
                    } else {
                        checkListDataDoesNotExist(path, data);
                        tx.remove(path);
                        tx.replace(path, data);
                        tx.replace(path, readData);
                    }
                } catch (RequestException e) {
                    tx.cancel();
                    throw e;
                }
                yield tx.commit();
            }
            case LAST -> {
                try {
                    tx.create(path, data);
                } catch (RequestException e) {
                    tx.cancel();
                    throw e;
                }
                yield tx.commit();
            }
            case BEFORE -> {
                try {
                    final var readData = tx.readList(path);
                    if (readData == null || readData.isEmpty()) {
                        tx.replace(path, data);
                    } else {
                        checkListDataDoesNotExist(path, data);
                        insertWithPointPost(tx, path, data, verifyNotNull(insert.pointArg()), readData, true);
                    }
                } catch (RequestException e) {
                    tx.cancel();
                    throw e;
                }
                yield tx.commit();
            }
            case AFTER -> {
                try {
                    final var readData = tx.readList(path);
                    if (readData == null || readData.isEmpty()) {
                        tx.replace(path, data);
                    } else {
                        checkListDataDoesNotExist(path, data);
                        insertWithPointPost(tx, path, data, verifyNotNull(insert.pointArg()), readData, false);
                    }
                } catch (RequestException e) {
                    tx.cancel();
                    throw e;
                }
                yield tx.commit();
            }
        };
    }

    @Override
    public final void patchData(final ServerRequest<DataYangPatchResult> request, final Data path,
            final PatchContext patch) {
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var tx = prepareWriteExecution();

        boolean noError = true;
        for (var patchEntity : patch.entities()) {
            if (noError) {
                final var targetNode = patchEntity.getDataPath().instance();
                final var editId = patchEntity.getEditId();

                switch (patchEntity.getOperation()) {
                    case Create:
                        try {
                            tx.create(targetNode, patchEntity.getNode());
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RequestException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.errors()));
                            noError = false;
                        }
                        break;
                    case Delete:
                        try {
                            tx.delete(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RequestException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.errors()));
                            noError = false;
                        }
                        break;
                    case Merge:
                        tx.ensureParentsByMerge(targetNode);
                        tx.merge(targetNode, patchEntity.getNode());
                        editCollection.add(new PatchStatusEntity(editId, true, null));
                        break;
                    case Replace:
                        tx.replace(targetNode, patchEntity.getNode());
                        editCollection.add(new PatchStatusEntity(editId, true, null));
                        break;
                    case Remove:
                        try {
                            tx.remove(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RequestException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.errors()));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(editId, false, List.of(
                            new RequestError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                                "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // We have errors
        if (!noError) {
            tx.cancel();
            request.completeWith(new DataYangPatchResult(
                new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), false, null)));
            return;
        }

        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                request.completeWith(new DataYangPatchResult(
                    new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), true, null)));
            }

            @Override
            public void onFailure(final Throwable cause) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                request.completeWith(new DataYangPatchResult(
                    new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), false,
                        decodeException(cause, "PATCH", null).errors())));
            }
        }, MoreExecutors.directExecutor());
    }

    private static void insertWithPointPost(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode data, final PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) throws RequestException {
        tx.remove(path);

        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }

        int lastInsertedPosition = 0;
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
            tx.replace(path.node(nodeChild.name()), nodeChild);
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
        }
    }

    /**
     * Check if child items do NOT already exists in List at specified {@code path}.
     *
     * @param data Data to be checked
     * @param path Path to be checked
     * @throws RequestException if data already exists.
     */
    private void checkListDataDoesNotExist(final YangInstanceIdentifier path, final NormalizedNode data)
            throws RequestException {
        if (data instanceof NormalizedNodeContainer<?> dataNode) {
            for (final var node : dataNode.body()) {
                final var nodePath = path.node(node.name());
                checkItemDoesNotExists(databind, exists(nodePath), nodePath);
            }
        } else {
            throw new RequestException("Unexpected node type: " + data.getClass().getName());
        }
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @paran databind the {@link DatabindContext}
     * @param path path to be checked
     * @throws RequestException if data already exists.
     */
    static void checkItemDoesNotExists(final DatabindContext databind, final ListenableFuture<Boolean> existsFuture,
            final YangInstanceIdentifier path) throws RequestException {
        if (syncAccess(existsFuture, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                new ErrorPath(databind, path));
        }
    }

    @NonNullByDefault
    static final void completeDataGET(final ServerRequest<DataGetResult> request, final @Nullable NormalizedNode node,
            final Data path, final NormalizedNodeWriterFactory writerFactory,
            final @Nullable ConfigurationMetadata metadata) {
        // Non-existing data
        if (node == null) {
            request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                "Request could not be completed because the relevant data model content does not exist"));
            return;
        }

        final var body = NormalizedFormattableBody.of(path, node, writerFactory);
        request.completeWith(metadata == null ? new DataGetResult(body)
            : new DataGetResult(body, metadata.entityTag(), metadata.lastModified()));
    }

    /**
     * Read specific type of data from data store via transaction. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param content      type of data to read (config, state, all)
     * @param path         the path to read
     * @param defaultsMode value of with-defaults parameter
     * @return {@link NormalizedNode}
     */
    // FIXME: NETCONF-1155: this method should asynchronous
    @VisibleForTesting
    final @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final WithDefaultsParam defaultsMode) throws RequestException {
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path);

                yield mergeConfigAndSTateDataIfNeeded(stateDataNode, defaultsMode == null ? configDataNode
                    : prepareDataByParamWithDef(configDataNode, path, defaultsMode.mode()));
            }
            case CONFIG -> {
                final var read = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path);
                yield defaultsMode == null ? read
                    : prepareDataByParamWithDef(read, path, defaultsMode.mode());
            }
            case NONCONFIG -> readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path);
        };
    }

    private @Nullable NormalizedNode readDataViaTransaction(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) throws RequestException {
        return syncAccess(read(store, path), path).orElse(null);
    }

    final NormalizedNode prepareDataByParamWithDef(final NormalizedNode readData, final YangInstanceIdentifier path,
            final WithDefaultsMode defaultsMode) throws RequestException {
        final boolean trim = switch (defaultsMode) {
            case Trim -> true;
            case Explicit -> false;
            case ReportAll, ReportAllTagged ->
                throw new RequestException("Unsupported with-defaults value %s", defaultsMode.getName());
        };

        // FIXME: we have this readily available in InstanceIdentifierContext
        final var ctxNode = databind.schemaTree().findChild(path).orElseThrow();
        if (readData instanceof ContainerNode container) {
            final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(container.name());
            buildCont(builder, container.body(), ctxNode, trim);
            return builder.build();
        } else if (readData instanceof MapEntryNode mapEntry) {
            if (!(ctxNode.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
                throw new IllegalStateException("Input " + mapEntry + " does not match " + ctxNode);
            }

            final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(mapEntry.name());
            buildMapEntryBuilder(builder, mapEntry.body(), ctxNode, trim, listSchema.getKeyDefinition());
            return builder.build();
        } else {
            throw new IllegalStateException("Unhandled data contract " + readData.contract());
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final Collection<@NonNull DataContainerChild> children, final DataSchemaContext ctxNode,
            final boolean trim, final List<QName> keys) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);

            if (child instanceof ContainerNode container) {
                appendContainer(builder, container, childCtx, trim);
            } else if (child instanceof MapNode map) {
                appendMap(builder, map, childCtx, trim);
            } else if (child instanceof LeafNode<?> leaf) {
                appendLeaf(builder, leaf, childCtx, trim, keys);
            } else {
                // FIXME: we should never hit this, throw an ISE if this ever happens
                LOG.debug("Ignoring unhandled child contract {}", child.contract());
            }
        }
    }

    private static void appendContainer(final DataContainerNodeBuilder<?, ?> builder, final ContainerNode container,
            final DataSchemaContext ctxNode, final boolean trim) {
        final var childBuilder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(container.name());
        buildCont(childBuilder, container.body(), ctxNode, trim);
        builder.withChild(childBuilder.build());
    }

    private static void appendLeaf(final DataContainerNodeBuilder<?, ?> builder, final LeafNode<?> leaf,
            final DataSchemaContext ctxNode, final boolean trim, final List<QName> keys) {
        if (!(ctxNode.dataSchemaNode() instanceof LeafSchemaNode leafSchema)) {
            throw new IllegalStateException("Input " + leaf + " does not match " + ctxNode);
        }

        // FIXME: Document now this works with the likes of YangInstanceIdentifier. I bet it does not.
        final var defaultVal = leafSchema.getType().getDefaultValue().orElse(null);

        // This is a combined check for when we need to emit the leaf.
        if (
            // We always have to emit key leaf values
            keys.contains(leafSchema.getQName())
            // trim == WithDefaultsParam.TRIM and the source is assumed to store explicit values:
            //
            //            When data is retrieved with a <with-defaults> parameter equal to
            //            'trim', data nodes MUST NOT be reported if they contain the schema
            //            default value.  Non-configuration data nodes containing the schema
            //            default value MUST NOT be reported.
            //
            || trim && (defaultVal == null || !defaultVal.equals(leaf.body()))
            // !trim == WithDefaultsParam.EXPLICIT and the source is assume to store explicit values... but I fail to
            // grasp what we are doing here... emit only if it matches default ???!!!
            // FIXME: The WithDefaultsParam.EXPLICIT says:
            //
            //            Data nodes set to the YANG default by the client are reported.
            //
            //        and RFC8040 (https://www.rfc-editor.org/rfc/rfc8040#page-60) says:
            //
            //            If the "with-defaults" parameter is set to "explicit", then the
            //            server MUST adhere to the default-reporting behavior defined in
            //            Section 3.3 of [RFC6243].
            //
            //        and then RFC6243 (https://www.rfc-editor.org/rfc/rfc6243#section-3.3) says:
            //
            //            When data is retrieved with a <with-defaults> parameter equal to
            //            'explicit', a data node that was set by a client to its schema
            //            default value MUST be reported.  A conceptual data node that would be
            //            set by the server to the schema default value MUST NOT be reported.
            //            Non-configuration data nodes containing the schema default value MUST
            //            be reported.
            //
            // (rovarga): The source reports explicitly-defined leaves and does *not* create defaults by itself.
            //            This seems to disregard the 'trim = true' case semantics (see above).
            //            Combining the above, though, these checks are missing the 'non-config' check, which would
            //            distinguish, but barring that this check is superfluous and results in the wrong semantics.
            //            Without that input, this really should be  covered by the previous case.
                || !trim && defaultVal != null && defaultVal.equals(leaf.body())) {
            builder.withChild(leaf);
        }
    }

    private static void appendMap(final DataContainerNodeBuilder<?, ?> builder, final MapNode map,
            final DataSchemaContext childCtx, final boolean trim) {
        if (!(childCtx.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
            throw new IllegalStateException("Input " + map + " does not match " + childCtx);
        }

        final var childBuilder = switch (map.ordering()) {
            case SYSTEM -> ImmutableNodes.newSystemMapBuilder();
            case USER -> ImmutableNodes.newUserMapBuilder();
        };
        buildList(childBuilder.withNodeIdentifier(map.name()), map.body(), childCtx, trim,
            listSchema.getKeyDefinition());
        builder.withChild(childBuilder.build());
    }

    private static void buildList(final CollectionNodeBuilder<MapEntryNode, ? extends MapNode> builder,
            final Collection<@NonNull MapEntryNode> entries, final DataSchemaContext ctxNode, final boolean trim,
            final List<@NonNull QName> keys) {
        for (var entry : entries) {
            final var childCtx = getChildContext(ctxNode, entry);
            final var mapEntryBuilder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(entry.name());
            buildMapEntryBuilder(mapEntryBuilder, entry.body(), childCtx, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final Collection<DataContainerChild> children, final DataSchemaContext ctxNode, final boolean trim) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);
            if (child instanceof ContainerNode container) {
                appendContainer(builder, container, childCtx, trim);
            } else if (child instanceof MapNode map) {
                appendMap(builder, map, childCtx, trim);
            } else if (child instanceof LeafNode<?> leaf) {
                appendLeaf(builder, leaf, childCtx, trim, List.of());
            }
        }
    }

    private static @NonNull DataSchemaContext getChildContext(final DataSchemaContext ctxNode,
            final NormalizedNode child) {
        final var childId = child.name();
        final var childCtx = ctxNode instanceof DataSchemaContext.Composite composite ? composite.childByArg(childId)
            : null;
        if (childCtx == null) {
            throw new NoSuchElementException("Cannot resolve child " + childId + " in " + ctxNode);
        }
        return childCtx;
    }

    static final NormalizedNode mergeConfigAndSTateDataIfNeeded(final NormalizedNode stateDataNode,
            final NormalizedNode configDataNode) throws RequestException {
        if (stateDataNode == null) {
            // No state, return config
            return configDataNode;
        }
        if (configDataNode == null) {
            // No config, return state
            return stateDataNode;
        }
        // merge config and state
        return mergeStateAndConfigData(stateDataNode, configDataNode);
    }

    /**
     * Merge state and config data into a single NormalizedNode.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode mergeStateAndConfigData(final @NonNull NormalizedNode stateDataNode,
            final @NonNull NormalizedNode configDataNode) throws RequestException {
        validateNodeMerge(stateDataNode, configDataNode);
        // FIXME: this check is bogus, as it confuses yang.data.api (NormalizedNode) with yang.model.api (RpcDefinition)
        if (configDataNode instanceof RpcDefinition) {
            return prepareRpcData(configDataNode, stateDataNode);
        } else {
            return prepareData(configDataNode, stateDataNode);
        }
    }

    /**
     * Validates whether the two NormalizedNodes can be merged.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     */
    private static void validateNodeMerge(final @NonNull NormalizedNode stateDataNode,
                                          final @NonNull NormalizedNode configDataNode) throws RequestException {
        final QNameModule moduleOfStateData = stateDataNode.name().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.name().getNodeType().getModule();
        if (!moduleOfStateData.equals(moduleOfConfigData)) {
            throw new RequestException("Unable to merge data from different modules.");
        }
    }

    /**
     * Prepare and map data for rpc.
     *
     * @param configDataNode data node of config data
     * @param stateDataNode  data node of state data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode prepareRpcData(final @NonNull NormalizedNode configDataNode,
                                                          final @NonNull NormalizedNode stateDataNode) {
        final var mapEntryBuilder = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.name());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(configDataNode.name().getNodeType()))
            .addChild(mapEntryBuilder.build())
            .build();
    }

    /**
     * Map node to map entry builder.
     *
     * @param dataNode        data node
     * @param mapEntryBuilder builder for mapping data
     */
    private static void mapRpcDataNode(final @NonNull NormalizedNode dataNode,
            final @NonNull DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        ((ContainerNode) dataNode).body().forEach(mapEntryBuilder::addChild);
    }

    /**
     * Prepare and map all data from DS.
     *
     * @param configDataNode data node of config data
     * @param stateDataNode  data node of state data
     * @return {@link NormalizedNode}
     */
    @SuppressWarnings("unchecked")
    private static @NonNull NormalizedNode prepareData(final @NonNull NormalizedNode configDataNode,
                                                       final @NonNull NormalizedNode stateDataNode) {
        return switch (configDataNode) {
            case UserMapNode configMap -> {
                final var builder = ImmutableNodes.newUserMapBuilder().withNodeIdentifier(configMap.name());
                mapValueToBuilder(configMap.body(), ((UserMapNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case SystemMapNode configMap -> {
                final var builder = ImmutableNodes.newSystemMapBuilder().withNodeIdentifier(configMap.name());
                mapValueToBuilder(configMap.body(), ((SystemMapNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case MapEntryNode configEntry -> {
                final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(configEntry.name());
                mapValueToBuilder(configEntry.body(), ((MapEntryNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case ContainerNode configContaienr -> {
                final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(configContaienr.name());
                mapValueToBuilder(configContaienr.body(), ((ContainerNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case ChoiceNode configChoice -> {
                final var builder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(configChoice.name());
                mapValueToBuilder(configChoice.body(), ((ChoiceNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UserLeafSetNode<?> userLeafSet -> {
                final var configLeafSet = (UserLeafSetNode<Object>) userLeafSet;
                final var builder = ImmutableNodes.<Object>newUserLeafSetBuilder()
                    .withNodeIdentifier(configLeafSet.name());
                mapValueToBuilder(configLeafSet.body(), ((UserLeafSetNode<Object>) stateDataNode).body(), builder);
                yield builder.build();
            }
            case SystemLeafSetNode<?> systemLeafSet -> {
                final var configLeafSet = (SystemLeafSetNode<Object>) systemLeafSet;
                final var builder = ImmutableNodes.<Object>newSystemLeafSetBuilder()
                    .withNodeIdentifier(configLeafSet.name());
                mapValueToBuilder(configLeafSet.body(), ((SystemLeafSetNode<Object>) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UnkeyedListNode configList -> {
                final var builder = ImmutableNodes.newUnkeyedListBuilder().withNodeIdentifier(configList.name());
                mapValueToBuilder(configList.body(), ((UnkeyedListNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UnkeyedListEntryNode configEntry -> {
                final var builder = ImmutableNodes.newUnkeyedListEntryBuilder().withNodeIdentifier(configEntry.name());
                mapValueToBuilder(configEntry.body(), ((UnkeyedListEntryNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            // config trumps oper
            case LeafNode<?> configLeaf -> configLeaf;
            case LeafSetEntryNode<?> configEntry -> configEntry;
            default -> throw new IllegalStateException("Unexpected node type: " + configDataNode.getClass().getName());
        };
    }

    /**
     * Map value from container node to builder.
     *
     * @param configData collection of config data nodes
     * @param stateData  collection of state data nodes
     * @param builder    builder
     */
    private static <T extends NormalizedNode> void mapValueToBuilder(
            final @NonNull Collection<T> configData, final @NonNull Collection<T> stateData,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        final var configMap = configData.stream().collect(Collectors.toMap(NormalizedNode::name, Function.identity()));
        final var stateMap = stateData.stream().collect(Collectors.toMap(NormalizedNode::name, Function.identity()));

        // merge config and state data of children with different identifiers
        mapDataToBuilder(configMap, stateMap, builder);

        // merge config and state data of children with the same identifiers
        mergeDataToBuilder(configMap, stateMap, builder);
    }

    /**
     * Map data with different identifiers to builder. Data with different identifiers can be just added
     * as childs to parent node.
     *
     * @param configMap map of config data nodes
     * @param stateMap  map of state data nodes
     * @param builder   - builder
     */
    private static <T extends NormalizedNode> void mapDataToBuilder(
            final @NonNull Map<PathArgument, T> configMap, final @NonNull Map<PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        configMap.entrySet().stream().filter(x -> !stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
        stateMap.entrySet().stream().filter(x -> !configMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
    }

    /**
     * Map data with the same identifiers to builder. Data with the same identifiers cannot be just added but we need to
     * go one level down with {@code prepareData} method.
     *
     * @param configMap immutable config data
     * @param stateMap  immutable state data
     * @param builder   - builder
     */
    @SuppressWarnings("unchecked")
    private static <T extends NormalizedNode> void mergeDataToBuilder(
            final @NonNull Map<PathArgument, T> configMap, final @NonNull Map<PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        // it is enough to process only config data because operational contains the same data
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild((T) prepareData(y.getValue(), stateMap.get(y.getKey()))));
    }

    /**
     * Synchronize access to a path resource, translating any failure to a {@link RequestException}.
     *
     * @param <T> The type being accessed
     * @param future Access future
     * @param path Path being accessed
     * @return The accessed value
     * @throws RequestException if commit fails
     */
    // FIXME: require DatabindPath.Data here
    static final <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path)
            throws RequestException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RequestException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestException("Interrupted while accessing " + path, e);
        }
    }

    final @NonNull RequestException decodeException(final Throwable ex, final String txType, final Data dataPath) {
        if (ex instanceof TransactionCommitFailedException) {
            // If device send some error message we want this message to get to client and not just to throw it away
            // or override it with new generic message. We search for NetconfDocumentedException that was send from
            // netconfSB and we create RequestException accordingly.
            for (var error : Throwables.getCausalChain(ex)) {
                if (error instanceof NetconfDocumentedException netconfError) {
                    return new RequestException(netconfError.getErrorType(), netconfError.getErrorTag(), ex);
                }
                if (error instanceof DocumentedException documentedError) {
                    final var errorTag = documentedError.getErrorTag();
                    if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} already exists",
                            dataPath != null ? dataPath.instance() : null);
                        return new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                            dataPath != null ? dataPath.toErrorPath() : null, ex);
                    } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} does not exist",
                            dataPath != null ? dataPath.instance() : null);
                        return new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                            "Data does not exist", dataPath != null ? dataPath.toErrorPath() : null, ex);
                    }
                }
            }

            return new RequestException("Transaction(" + txType + ") not committed correctly", ex);
        }

        return new RequestException("Transaction(" + txType + ") failed", ex);
    }
}
