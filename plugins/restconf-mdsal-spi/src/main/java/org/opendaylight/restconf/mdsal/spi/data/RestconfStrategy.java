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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.mdsal.spi.util.ServerDataOperationsUtil;
import org.opendaylight.restconf.server.api.CreateResourceResult;
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
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline class for {@link ServerDataOperations} implementations.
 *
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

    protected RestconfStrategy(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    /**
     * Lock the entire datastore.
     *
     * @return A {@link RestconfTransaction}. This transaction needs to be either committed or canceled before doing
     *         anything else.
     */
    protected abstract RestconfTransaction prepareWriteExecution();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    protected abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store,
        YangInstanceIdentifier path);

    /**
     * Check if data already exists in the configuration datastore.
     *
     * @param path the data object path
     */
    // FIXME: this method should be hosted in RestconfTransaction
    // FIXME: this method should only be needed in MdsalRestconfStrategy
    protected abstract ListenableFuture<Boolean> exists(YangInstanceIdentifier path);

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
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public final void putData(final ServerRequest<DataPutResult> request, final Data path, final NormalizedNode data) {
        final var instance = path.instance();
        final Boolean exists;
        try {
            exists = ServerDataOperationsUtil.syncAccess(exists(instance), instance);
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
            exists = ServerDataOperationsUtil.syncAccess(exists(instance), instance);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        final ListenableFuture<? extends CommitInfo> commitFuture;
        final var parentPath = instance.coerceParent();
        try {
            ServerDataOperationsUtil.checkListAndOrderedType(parentPath, path.databind());
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
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "PUT", path));
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
            ServerDataOperationsUtil.checkListAndOrderedType(path.instance(), path.databind());
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
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "POST", path));
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
                        ServerDataOperationsUtil.decodeException(cause, "PATCH", path).errors())));
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
    protected final @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final WithDefaultsParam defaultsMode) throws RequestException {
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path);

                yield ServerDataOperationsUtil.mergeConfigAndSTateDataIfNeeded(stateDataNode, defaultsMode == null
                    ? configDataNode : ServerDataOperationsUtil.prepareDataByParamWithDef(configDataNode, path,
                    databind, defaultsMode.mode()));
            }
            case CONFIG -> {
                final var read = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path);
                yield defaultsMode == null ? read
                    : ServerDataOperationsUtil.prepareDataByParamWithDef(read, path, databind, defaultsMode.mode());
            }
            case NONCONFIG -> readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path);
        };
    }

    private @Nullable NormalizedNode readDataViaTransaction(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) throws RequestException {
        return ServerDataOperationsUtil.syncAccess(read(store, path), path).orElse(null);
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
    public static final <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path)
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
}
