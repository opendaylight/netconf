/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierSerializer;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.with.defaults.rev110601.WithDefaultsMode;
import org.opendaylight.yangtools.yang.common.Empty;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SubmoduleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline execution strategy for various RESTCONF operations.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
// FIXME: it seems the first three operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
public abstract class RestconfStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStrategy.class);

    private final @NonNull DatabindContext databind;
    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final DOMYangTextSourceProvider sourceProvider;
    private final DOMRpcService rpcService;

    RestconfStrategy(final DatabindContext databind, final ImmutableMap<QName, RpcImplementation> localRpcs,
            final @Nullable DOMRpcService rpcService, final DOMYangTextSourceProvider sourceProvider) {
        this.databind = requireNonNull(databind);
        this.localRpcs = requireNonNull(localRpcs);
        this.rpcService = rpcService;
        this.sourceProvider = sourceProvider;
    }

    /**
     * Look up the appropriate strategy for a particular mount point.
     *
     * @param databind {@link DatabindContext} of target mount point
     * @param mountPoint Target mount point
     * @return A strategy, or null if the mount point does not expose a supported interface
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @Nullable RestconfStrategy forMountPoint(final DatabindContext databind,
            final DOMMountPoint mountPoint) {
        final var rpcService = mountPoint.getService(DOMRpcService.class).orElse(null);
        final var sourceProvider = mountPoint.getService(DOMSchemaService.class)
            .flatMap(schema -> Optional.ofNullable(schema.getExtensions().getInstance(DOMYangTextSourceProvider.class)))
            .orElse(null);

        final var netconfService = mountPoint.getService(NetconfDataTreeService.class);
        if (netconfService.isPresent()) {
            return new NetconfRestconfStrategy(databind, netconfService.orElseThrow(), rpcService, sourceProvider);
        }
        final var dataBroker = mountPoint.getService(DOMDataBroker.class);
        if (dataBroker.isPresent()) {
            return new MdsalRestconfStrategy(databind, dataBroker.orElseThrow(), rpcService, sourceProvider);
        }
        return null;
    }

    public final @NonNull DatabindContext databind() {
        return databind;
    }

    public final @NonNull EffectiveModelContext modelContext() {
        return databind.modelContext();
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
     * Read data selected using fields from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the parent data object path
     * @param fields paths to selected fields relative to parent path
     * @return a ListenableFuture containing the result of the read
     */
    abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, YangInstanceIdentifier path,
        List<YangInstanceIdentifier> fields);

    /**
     * Check if data already exists in the configuration datastore.
     *
     * @param path the data object path
     * @return a ListenableFuture containing the result of the check
     */
    // FIXME: this method should be hosted in RestconfTransaction
    // FIXME: this method should only be needed in MdsalRestconfStrategy
    abstract ListenableFuture<Boolean> exists(YangInstanceIdentifier path);

    /**
     * Delete data from the configuration datastore. If the data does not exist, this operation will fail, as outlined
     * in <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.7">RFC8040 section 4.7</a>
     *
     * @param path Path to delete
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> delete(final YangInstanceIdentifier path) {
        final var ret = new SettableRestconfFuture<Empty>();
        delete(ret, requireNonNull(path));
        return ret;
    }

    abstract void delete(@NonNull SettableRestconfFuture<Empty> future, @NonNull YangInstanceIdentifier path);

    /**
     * Merge data into the configuration datastore, as outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 section 4.6.1</a>.
     *
     * @param path Path to merge
     * @param data Data to merge
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if any argument is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> merge(final YangInstanceIdentifier path, final NormalizedNode data) {
        final var ret = new SettableRestconfFuture<Empty>();
        merge(ret, requireNonNull(path), requireNonNull(data));
        return ret;
    }

    private void merge(final @NonNull SettableRestconfFuture<Empty> future, final @NonNull YangInstanceIdentifier path,
            final @NonNull NormalizedNode data) {
        final var tx = prepareWriteExecution();
        // FIXME: this method should be further specialized to eliminate this call -- it is only needed for MD-SAL
        tx.ensureParentsByMerge(path);
        tx.merge(path, data);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                future.set(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setFailure(TransactionUtil.decodeException(cause, "MERGE", path, modelContext()));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Check mount point and prepare variables for put data to DS.
     *
     * @param path    path of data
     * @param data    data
     * @param insert  {@link Insert}
     * @return A {@link DataPutResult}
     */
    public final RestconfFuture<DataPutResult> putData(final YangInstanceIdentifier path,
            final NormalizedNode data, final @Nullable Insert insert) {
        final var exists = TransactionUtil.syncAccess(exists(path), path);

        final ListenableFuture<? extends CommitInfo> commitFuture;
        if (insert != null) {
            final var parentPath = path.coerceParent();
            checkListAndOrderedType(parentPath);
            commitFuture = insertAndCommitPut(path, data, insert, parentPath);
        } else {
            commitFuture = replaceAndCommit(prepareWriteExecution(), path, data);
        }

        final var ret = new SettableRestconfFuture<DataPutResult>();

        Futures.addCallback(commitFuture, new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                ret.set(exists ? DataPutResult.REPLACED : DataPutResult.CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setFailure(TransactionUtil.decodeException(cause, "PUT", path, modelContext()));
            }
        }, MoreExecutors.directExecutor());

        return ret;
    }

    private ListenableFuture<? extends CommitInfo> insertAndCommitPut(final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull Insert insert, final YangInstanceIdentifier parentPath) {
        final var tx = prepareWriteExecution();

        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data);
                }
                tx.remove(parentPath);
                tx.replace(path, data);
                tx.replace(parentPath, readData);
                yield tx.commit();
            }
            case LAST -> replaceAndCommit(tx, path, data);
            case BEFORE -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data);
                }
                insertWithPointPut(tx, path, data, verifyNotNull(insert.pointArg()), readData, true);
                yield tx.commit();
            }
            case AFTER -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data);
                }
                insertWithPointPut(tx, path, data, verifyNotNull(insert.pointArg()), readData, false);
                yield tx.commit();
            }
        };
    }

    private void insertWithPointPut(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) {
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
        final var emptySubtree = ImmutableNodes.fromInstanceId(modelContext(), path.getParent());
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

    private DataSchemaNode checkListAndOrderedType(final YangInstanceIdentifier path) {
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
        throw new RestconfDocumentedException(message, ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
    }

    /**
     * Check mount point and prepare variables for post data.
     *
     * @param path    path
     * @param data    data
     * @param insert  {@link Insert}
     * @return A {@link RestconfFuture}
     */
    public final @NonNull RestconfFuture<CreateResource> postData(final YangInstanceIdentifier path,
            final NormalizedNode data, final @Nullable Insert insert) {
        final ListenableFuture<? extends CommitInfo> future;
        if (insert != null) {
            final var parentPath = path.coerceParent();
            checkListAndOrderedType(parentPath);
            future = insertAndCommitPost(path, data, insert, parentPath);
        } else {
            future = createAndCommit(prepareWriteExecution(), path, data);
        }

        final var ret = new SettableRestconfFuture<CreateResource>();
        Futures.addCallback(future, new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                ret.set(new CreateResource(new YangInstanceIdentifierSerializer(databind).serializePath(
                    data instanceof MapNode mapData && !mapData.isEmpty()
                        ? path.node(mapData.body().iterator().next().name()) : path)));
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setFailure(TransactionUtil.decodeException(cause, "POST", path, modelContext()));
            }

        }, MoreExecutors.directExecutor());
        return ret;
    }

    private ListenableFuture<? extends CommitInfo> insertAndCommitPost(final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull Insert insert, final YangInstanceIdentifier parent) {
        final var grandParent = parent.coerceParent();
        final var tx = prepareWriteExecution();

        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = tx.readList(grandParent);
                if (readData == null || readData.isEmpty()) {
                    tx.replace(path, data);
                } else {
                    checkItemDoesNotExists(exists(path), path);
                    tx.remove(grandParent);
                    tx.replace(path, data);
                    tx.replace(grandParent, readData);
                }
                yield tx.commit();
            }
            case LAST -> createAndCommit(tx, path, data);
            case BEFORE -> {
                final var readData = tx.readList(grandParent);
                if (readData == null || readData.isEmpty()) {
                    tx.replace(path, data);
                } else {
                    checkItemDoesNotExists(exists(path), path);
                    insertWithPointPost(tx, path, data, verifyNotNull(insert.pointArg()), readData, grandParent, true);
                }
                yield tx.commit();
            }
            case AFTER -> {
                final var readData = tx.readList(grandParent);
                if (readData == null || readData.isEmpty()) {
                    tx.replace(path, data);
                } else {
                    checkItemDoesNotExists(exists(path), path);
                    insertWithPointPost(tx, path, data, verifyNotNull(insert.pointArg()), readData, grandParent, false);
                }
                yield tx.commit();
            }
        };
    }

    /**
     * Process edit operations of one {@link PatchContext}.
     *
     * @param patch Patch context to be processed
     * @return {@link PatchStatusContext}
     */
    public final @NonNull RestconfFuture<PatchStatusContext> patchData(final PatchContext patch) {
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var tx = prepareWriteExecution();

        boolean noError = true;
        for (var patchEntity : patch.entities()) {
            if (noError) {
                final var targetNode = patchEntity.getTargetNode();
                final var editId = patchEntity.getEditId();

                switch (patchEntity.getOperation()) {
                    case Create:
                        try {
                            tx.create(targetNode, patchEntity.getNode());
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Delete:
                        try {
                            tx.delete(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Merge:
                        try {
                            tx.ensureParentsByMerge(targetNode);
                            tx.merge(targetNode, patchEntity.getNode());
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Replace:
                        try {
                            tx.replace(targetNode, patchEntity.getNode());
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Remove:
                        try {
                            tx.remove(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(editId, false, List.of(
                            new RestconfError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                                "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        final var ret = new SettableRestconfFuture<PatchStatusContext>();
        // We have errors
        if (!noError) {
            tx.cancel();
            ret.set(new PatchStatusContext(modelContext(), patch.patchId(), List.copyOf(editCollection), false, null));
            return ret;
        }

        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                ret.set(new PatchStatusContext(modelContext(), patch.patchId(), List.copyOf(editCollection), true,
                    null));
            }

            @Override
            public void onFailure(final Throwable cause) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                ret.set(new PatchStatusContext(modelContext(), patch.patchId(), List.copyOf(editCollection), false,
                    TransactionUtil.decodeException(cause, "PATCH", null, modelContext()).getErrors()));
            }
        }, MoreExecutors.directExecutor());

        return ret;
    }

    private void insertWithPointPost(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode data, final PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final YangInstanceIdentifier grandParentPath, final boolean before) {
        tx.remove(grandParentPath);

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
        final var emptySubtree = ImmutableNodes.fromInstanceId(modelContext(), grandParentPath);
        tx.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
            tx.replace(grandParentPath.node(nodeChild.name()), nodeChild);
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data);
            }
        }
    }

    private static ListenableFuture<? extends CommitInfo> createAndCommit(final RestconfTransaction tx,
            final YangInstanceIdentifier path, final NormalizedNode data) {
        try {
            tx.create(path, data);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            tx.cancel();
            throw e;
        }

        return tx.commit();
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @param path         Path to be checked
     * @throws RestconfDocumentedException if data already exists.
     */
    static void checkItemDoesNotExists(final ListenableFuture<Boolean> existsFuture,
            final YangInstanceIdentifier path) {
        if (TransactionUtil.syncAccess(existsFuture, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                path);
        }
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
    public @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final WithDefaultsParam defaultsMode) {
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

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param content  type of data to read (config, state, all)
     * @param path     the parent path to read
     * @param withDefa value of with-defaults parameter
     * @param fields   paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    // FIXME: NETCONF-1155: this method should asynchronous
    public @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final @Nullable WithDefaultsParam withDefa,
            final @NonNull List<YangInstanceIdentifier> fields) {
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);

                yield mergeConfigAndSTateDataIfNeeded(stateDataNode, withDefa == null ? configDataNode
                    : prepareDataByParamWithDef(configDataNode, path, withDefa.mode()));
            }
            case CONFIG -> {
                final var read = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, path, withDefa.mode());
            }
            case NONCONFIG -> readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
        };
    }

    private @Nullable NormalizedNode readDataViaTransaction(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return TransactionUtil.syncAccess(read(store, path), path).orElse(null);
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link RestconfStrategy} with
     * specified subtrees that should only be read.
     *
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param closeTransactionChain if it is set to {@code true}, after transaction it will close transactionChain
     *                              in {@link RestconfStrategy} if any
     * @param fields                paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final @NonNull YangInstanceIdentifier path, final @NonNull List<YangInstanceIdentifier> fields) {
        return TransactionUtil.syncAccess(read(store, path, fields), path).orElse(null);
    }

    private NormalizedNode prepareDataByParamWithDef(final NormalizedNode readData, final YangInstanceIdentifier path,
            final WithDefaultsMode defaultsMode) {
        final boolean trim = switch (defaultsMode) {
            case Trim -> true;
            case Explicit -> false;
            case ReportAll, ReportAllTagged -> throw new RestconfDocumentedException(
                "Unsupported with-defaults value " + defaultsMode.getName());
        };

        // FIXME: we have this readily available in InstanceIdentifierContext
        final var ctxNode = databind.schemaTree().findChild(path).orElseThrow();
        if (readData instanceof ContainerNode container) {
            final var builder = Builders.containerBuilder().withNodeIdentifier(container.name());
            buildCont(builder, container.body(), ctxNode, trim);
            return builder.build();
        } else if (readData instanceof MapEntryNode mapEntry) {
            if (!(ctxNode.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
                throw new IllegalStateException("Input " + mapEntry + " does not match " + ctxNode);
            }

            final var builder = Builders.mapEntryBuilder().withNodeIdentifier(mapEntry.name());
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
        final var childBuilder = Builders.containerBuilder().withNodeIdentifier(container.name());
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
            case SYSTEM -> Builders.mapBuilder();
            case USER -> Builders.orderedMapBuilder();
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
            final var mapEntryBuilder = Builders.mapEntryBuilder().withNodeIdentifier(entry.name());
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

    private static NormalizedNode mergeConfigAndSTateDataIfNeeded(final NormalizedNode stateDataNode,
                                                                  final NormalizedNode configDataNode) {
        // if no data exists
        if (stateDataNode == null && configDataNode == null) {
            return null;
        }

        // return config data
        if (stateDataNode == null) {
            return configDataNode;
        }

        // return state data
        if (configDataNode == null) {
            return stateDataNode;
        }

        // merge data from config and state
        return mergeStateAndConfigData(stateDataNode, configDataNode);
    }

    /**
     * Merge state and config data into a single NormalizedNode.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode mergeStateAndConfigData(
            final @NonNull NormalizedNode stateDataNode, final @NonNull NormalizedNode configDataNode) {
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
                                          final @NonNull NormalizedNode configDataNode) {
        final QNameModule moduleOfStateData = stateDataNode.name().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.name().getNodeType().getModule();
        if (!moduleOfStateData.equals(moduleOfConfigData)) {
            throw new RestconfDocumentedException("Unable to merge data from different modules.");
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
        final var mapEntryBuilder = Builders.mapEntryBuilder()
            .withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.name());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return Builders.mapBuilder()
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
        if (configDataNode instanceof UserMapNode configMap) {
            final var builder = Builders.orderedMapBuilder().withNodeIdentifier(configMap.name());
            mapValueToBuilder(configMap.body(), ((UserMapNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof SystemMapNode configMap) {
            final var builder = Builders.mapBuilder().withNodeIdentifier(configMap.name());
            mapValueToBuilder(configMap.body(), ((SystemMapNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof MapEntryNode configEntry) {
            final var builder = Builders.mapEntryBuilder().withNodeIdentifier(configEntry.name());
            mapValueToBuilder(configEntry.body(), ((MapEntryNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof ContainerNode configContaienr) {
            final var builder = Builders.containerBuilder().withNodeIdentifier(configContaienr.name());
            mapValueToBuilder(configContaienr.body(), ((ContainerNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof ChoiceNode configChoice) {
            final var builder = Builders.choiceBuilder().withNodeIdentifier(configChoice.name());
            mapValueToBuilder(configChoice.body(), ((ChoiceNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafNode configLeaf) {
            // config trumps oper
            return configLeaf;
        } else if (configDataNode instanceof UserLeafSetNode) {
            final var configLeafSet = (UserLeafSetNode<Object>) configDataNode;
            final var builder = Builders.<Object>orderedLeafSetBuilder().withNodeIdentifier(configLeafSet.name());
            mapValueToBuilder(configLeafSet.body(), ((UserLeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof SystemLeafSetNode) {
            final var configLeafSet = (SystemLeafSetNode<Object>) configDataNode;
            final var builder = Builders.<Object>leafSetBuilder().withNodeIdentifier(configLeafSet.name());
            mapValueToBuilder(configLeafSet.body(), ((SystemLeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetEntryNode<?> configEntry) {
            // config trumps oper
            return configEntry;
        } else if (configDataNode instanceof UnkeyedListNode configList) {
            final var builder = Builders.unkeyedListBuilder().withNodeIdentifier(configList.name());
            mapValueToBuilder(configList.body(), ((UnkeyedListNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof UnkeyedListEntryNode configEntry) {
            final var builder = Builders.unkeyedListEntryBuilder().withNodeIdentifier(configEntry.name());
            mapValueToBuilder(configEntry.body(), ((UnkeyedListEntryNode) stateDataNode).body(), builder);
            return builder.build();
        } else {
            throw new RestconfDocumentedException("Unexpected node type: " + configDataNode.getClass().getName());
        }
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

    public @NonNull RestconfFuture<OperationsPostResult> invokeRpc(final URI restconfURI, final QName type,
            final OperationInput input) {
        final var local = localRpcs.get(type);
        if (local != null) {
            return local.invoke(restconfURI, input);
        }
        if (rpcService == null) {
            LOG.debug("RPC invocation is not available");
            return RestconfFuture.failed(new RestconfDocumentedException("RPC invocation is not available",
                ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED));
        }

        final var ret = new SettableRestconfFuture<OperationsPostResult>();
        Futures.addCallback(rpcService.invokeRpc(requireNonNull(type), input.input()),
            new FutureCallback<DOMRpcResult>() {
                @Override
                public void onSuccess(final DOMRpcResult response) {
                    final var errors = response.errors();
                    if (errors.isEmpty()) {
                        ret.set(input.newOperationOutput(response.value()));
                    } else {
                        LOG.debug("RPC invocation reported {}", response.errors());
                        ret.setFailure(new RestconfDocumentedException("RPC implementation reported errors", null,
                            response.errors()));
                    }
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.debug("RPC invocation failed, cause");
                    if (cause instanceof RestconfDocumentedException ex) {
                        ret.setFailure(ex);
                    } else {
                        // TODO: YangNetconfErrorAware if we ever get into a broader invocation scope
                        ret.setFailure(new RestconfDocumentedException(cause,
                            new RestconfError(ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause.getMessage())));
                    }
                }
            }, MoreExecutors.directExecutor());
        return ret;
    }

    public @NonNull RestconfFuture<CharSource> resolveSource(final SourceIdentifier source,
            final Class<? extends SchemaSourceRepresentation> representation) {
        final var src = requireNonNull(source);
        if (YangTextSchemaSource.class.isAssignableFrom(representation)) {
            if (sourceProvider != null) {
                final var ret = new SettableRestconfFuture<CharSource>();
                Futures.addCallback(sourceProvider.getSource(src), new FutureCallback<YangTextSchemaSource>() {
                    @Override
                    public void onSuccess(final YangTextSchemaSource result) {
                        ret.set(result);
                    }

                    @Override
                    public void onFailure(final Throwable cause) {
                        ret.setFailure(cause instanceof RestconfDocumentedException e ? e
                            : new RestconfDocumentedException(cause.getMessage(), ErrorType.RPC,
                                ErrorTag.OPERATION_FAILED, cause));
                    }
                }, MoreExecutors.directExecutor());
                return ret;
            }
            return exportSource(modelContext(), src, YangCharSource::new, YangCharSource::new);
        }
        if (YinTextSchemaSource.class.isAssignableFrom(representation)) {
            return exportSource(modelContext(), src, YinCharSource.OfModule::new, YinCharSource.OfSubmodule::new);
        }
        return RestconfFuture.failed(new RestconfDocumentedException(
            "Unsupported source representation " + representation.getName()));
    }

    private static @NonNull RestconfFuture<CharSource> exportSource(final EffectiveModelContext modelContext,
            final SourceIdentifier source, final Function<ModuleEffectiveStatement, CharSource> moduleCtor,
            final BiFunction<ModuleEffectiveStatement, SubmoduleEffectiveStatement, CharSource> submoduleCtor) {
        // If the source identifies a module, things are easy
        final var name = source.name().getLocalName();
        final var optRevision = Optional.ofNullable(source.revision());
        final var optModule = modelContext.findModule(name, optRevision);
        if (optModule.isPresent()) {
            return RestconfFuture.of(moduleCtor.apply(optModule.orElseThrow().asEffectiveStatement()));
        }

        // The source could be a submodule, which we need to hunt down
        for (var module : modelContext.getModules()) {
            for (var submodule : module.getSubmodules()) {
                if (name.equals(submodule.getName()) && optRevision.equals(submodule.getRevision())) {
                    return RestconfFuture.of(submoduleCtor.apply(module.asEffectiveStatement(),
                        submodule.asEffectiveStatement()));
                }
            }
        }

        final var sb = new StringBuilder().append("Source ").append(source.name().getLocalName());
        optRevision.ifPresent(rev -> sb.append('@').append(rev));
        sb.append(" not found");
        return RestconfFuture.failed(new RestconfDocumentedException(sb.toString(),
            ErrorType.APPLICATION, ErrorTag.DATA_MISSING));
    }

}
