/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Verify.verifyNotNull;
import static org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes.fromInstanceId;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.mdsal.spi.data.RestconfStrategy;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchEntity;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerDataOperations;
import org.opendaylight.restconf.server.spi.ApiPathCanonizer;
import org.opendaylight.restconf.server.spi.Insert;
import org.opendaylight.restconf.server.spi.NormalizedFormattableBody;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDataOperations extends AbstractServerDataOperations {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataOperations.class);
    private static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());
    private static final @NonNull DataPutResult PUT_CREATED = new DataPutResult(true);
    private static final @NonNull DataPutResult PUT_REPLACED = new DataPutResult(false);
    private static final @NonNull DataPatchResult PATCH_EMPTY = new DataPatchResult();
    private static final DataGetParams CONFIG_PARAM = new DataGetParams(ContentParam.CONFIG, null, null, null);

    private final DataOperationService dataStoreService;

    public NetconfDataOperations(final DataOperationService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        // FIXME: Get defaultOperation
        var futureChain = dataStoreService.createData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final ApiPath apiPath;
                try {
                    apiPath = new ApiPathCanonizer(path.databind()).dataToApiPath(
                        data.body() instanceof MapNode mapData && !mapData.isEmpty()
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
        }, Executors.newSingleThreadExecutor());
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final Insert insert, final NormalizedNode data) {
        try {
            checkListAndOrderedType(path);
        } catch (RequestException cause) {
            request.completeWith(cause);
            return;
        }
        ListenableFuture<? extends DOMRpcResult> futureChain;
        try {
            futureChain = insertCreate(path, data, insert);
        } catch (RequestException cause) {
            futureChain = Futures.immediateFailedFuture(cause);
        }
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final ApiPath apiPath;
                try {
                    apiPath = new ApiPathCanonizer(path.databind()).dataToApiPath(
                        data.body() instanceof MapNode mapData && !mapData.isEmpty()
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
        }, Executors.newSingleThreadExecutor());
    }


    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        var futureChain = dataStoreService.deleteData(path);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "DELETE", path));
            }
        }, MoreExecutors.directExecutor());

    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        final var readFuture = dataStoreService.getData(path, params);

        Futures.addCallback(readFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode> result) {
                // Non-existing data
                if (result.isEmpty()) {
                    request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                        "Request could not be completed because the relevant data model content does not exist"));
                    return;
                }
                final var normalizedNode = result.orElseThrow();
                final var writerFactory = NormalizedNodeWriterFactory.of(params.depth());
                final var body = NormalizedFormattableBody.of(path, normalizedNode, writerFactory);
                request.completeWith(new DataGetResult(body));
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "GET", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final Data path, final NormalizedNode data) {
        var futureChain = dataStoreService.mergeData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                // TODO: extract details once DOMRpcResult can communicate them
                request.completeWith(PATCH_EMPTY);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchContext patch) {
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var entities = patch.entities();
        ListenableFuture<? extends DOMRpcResult> futureChain = RPC_SUCCESS;

        for (int i = 0; i < entities.size() ; i++) {
            final var currentEntity = entities.get(i);
            final var targetNode = currentEntity.getDataPath();
            final var previousEntity = i > 0 ? entities.get(i - 1) : null;
            if (previousEntity == null) {
                futureChain = addIntoFutureChain(futureChain,
                    () -> patchRequest(targetNode, currentEntity.getNode(), currentEntity.getOperation()));
            } else {
                futureChain = chainPatchEditTransaction(futureChain,
                    () -> patchRequest(targetNode, currentEntity.getNode(), currentEntity.getOperation()),
                    previousEntity, editCollection);
            }
        }

        if (entities.isEmpty()) {
            futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);
        } else {
            futureChain = chainPatchEditTransaction(futureChain, dataStoreService::commit,
                entities.get(entities.size() - 1), editCollection);
        }

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(new DataYangPatchResult(
                    new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), true, null)));
            }

            @Override
            public void onFailure(final Throwable cause) {
                if (containsFailure(editCollection)) {
                    request.completeWith(new DataYangPatchResult(
                        new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), false, null)));
                } else {
                    // if errors occurred during transaction commit then patch failed and global errors are reported
                    request.completeWith(new DataYangPatchResult(
                        new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), false,
                            decodeException(cause, "PATCH", path).errors())));
                }
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final NormalizedNode data) {
        final Boolean exists;
        try {
            exists = RestconfStrategy.syncAccess(exists(path), path.instance());
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        var futureChain = dataStoreService.putData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final Insert insert,
            final NormalizedNode data) {
        final var instance = path.instance();
        final Boolean exists;
        final Data parentPath;
        try {
            exists = RestconfStrategy.syncAccess(exists(path), instance);
            parentPath = RestconfStrategy.getConceptualParent(path);
            checkListAndOrderedType(parentPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        ListenableFuture<? extends DOMRpcResult> futureChain;
        try {
            futureChain = insertPut(path, data, insert, parentPath);
        } catch (RequestException cause) {
            futureChain = Futures.immediateFailedFuture(cause);
        }
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<? extends DOMRpcResult> patchRequest(final Data path, final NormalizedNode data,
            final Operation operation) {
        try {
            return switch (operation) {
                case Create -> dataStoreService.createData(path, data);
                case Merge -> dataStoreService.mergeData(path, data);
                case Replace -> dataStoreService.putData(path, data);
                case Delete -> dataStoreService.deleteData(path);
                case Remove -> dataStoreService.removeData(path);
                default -> throw new RequestException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                    "Not supported Yang Patch operation");
            };
        } catch (RequestException cause) {
            return Futures.immediateFailedFuture(cause);
        }
    }

    private ListenableFuture<? extends DOMRpcResult> insertPut(final Data path, final NormalizedNode data,
            final @NonNull Insert insert, final Data parentPath) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = dataStoreService.getData(parentPath, CONFIG_PARAM);
                yield Futures.transformAsync(readData, readNode -> {
                    final ListenableFuture<? extends DOMRpcResult> result;
                    if (readNode.isEmpty()) {
                        result = dataStoreService.putData(path, data);
                    } else {
                        var futureChain = dataStoreService.removeData(parentPath);
                        futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
                        result = addIntoFutureChain(futureChain,
                            () -> dataStoreService.putData(parentPath, readNode.orElseThrow()));
                    }
                    return result;
                }, MoreExecutors.directExecutor());
            }
            case LAST -> dataStoreService.putData(path, data);
            case BEFORE -> {
                final var readData = dataStoreService.getData(parentPath, CONFIG_PARAM);
                yield Futures.transformAsync(readData,
                    readNode -> {
                        final ListenableFuture<? extends DOMRpcResult> result;
                        if (readNode.isEmpty()) {
                            result = dataStoreService.putData(path, data);
                        } else {
                            result = insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()),
                                (NormalizedNodeContainer<?>) readNode.orElse(null), true);
                        }
                        return result;
                    }, MoreExecutors.directExecutor());
            }
            case AFTER -> {
                final var readData = dataStoreService.getData(parentPath, CONFIG_PARAM);
                yield Futures.transformAsync(readData,
                    readNode -> {
                        final ListenableFuture<? extends DOMRpcResult> result;
                        if (readNode.isEmpty()) {
                            result = dataStoreService.putData(path, data);
                        } else {
                            result = insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()),
                                (NormalizedNodeContainer<?>) readNode.orElse(null), false);
                        }
                        return result;
                    }, MoreExecutors.directExecutor());
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPut(final Data path, final Data parentPath,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) {
        ListenableFuture<? extends DOMRpcResult> futureChain = dataStoreService.removeData(parentPath);

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

        final var emptySubtree = fromInstanceId(parentPath.databind().modelContext(), parentPath.instance());
        futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.mergeData(parentPath, emptySubtree));

        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
            final var conChild = childPath(parentPath, List.of(nodeChild.name()));
            futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(conChild,
                nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
        }
        return futureChain;
    }

    private ListenableFuture<? extends DOMRpcResult> insertCreate(final Data path, final NormalizedNode data,
            final @NonNull Insert insert) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = dataStoreService.getData(path, CONFIG_PARAM);
                yield Futures.transformAsync(readData,
                    readNode -> {
                        final ListenableFuture<? extends DOMRpcResult> result;
                        if (readNode.isEmpty()) {
                            result = dataStoreService.putData(path, data);
                        } else {
                            checkListDataDoesNotExist(path, data);
                            var futureChain = dataStoreService.removeData(path);
                            futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
                            result = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path,
                                readNode.orElseThrow()));
                        }
                        return result;
                    }, MoreExecutors.directExecutor());
            }
            case LAST -> dataStoreService.createData(path, data);
            case BEFORE -> {
                final var readData = dataStoreService.getData(path, CONFIG_PARAM);
                yield Futures.transformAsync(readData,
                    readNode -> {
                        final ListenableFuture<? extends DOMRpcResult> result;
                        if (readNode.isEmpty()) {
                            result = dataStoreService.putData(path, data);
                        } else {
                            checkListDataDoesNotExist(path, data);
                            result = insertWithPointPost(path, data, verifyNotNull(insert.pointArg()),
                                (NormalizedNodeContainer<?>) readNode.orElse(null), true);
                        }
                        return result;
                    }, MoreExecutors.directExecutor());
            }
            case AFTER -> {
                final var readData = dataStoreService.getData(path, CONFIG_PARAM);
                yield Futures.transformAsync(readData,
                    readNode -> {
                        final ListenableFuture<? extends DOMRpcResult> result;
                        if (readNode.isEmpty()) {
                            result = dataStoreService.putData(path, data);
                        } else {
                            checkListDataDoesNotExist(path, data);
                            result = insertWithPointPost(path, data, verifyNotNull(insert.pointArg()),
                                (NormalizedNodeContainer<?>) readNode.orElse(null), false);
                        }
                        return result;
                    }, MoreExecutors.directExecutor());
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPost(final Data path, final NormalizedNode data,
            final PathArgument pointArg, final NormalizedNodeContainer<?> readList, final boolean before) {
        var futureChain = dataStoreService.removeData(path);

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
                // FIXME: Should not be create here?
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
            final var childPath = childPath(path, List.of(nodeChild.name()));
            futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(childPath, nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
        }
        return futureChain;
    }

    /**
     * Check if child items do NOT already exists in List at specified {@code path}.
     *
     * @param data Data to be checked
     * @param path Path to be checked
     * @throws RequestException if data already exists.
     */
    private void checkListDataDoesNotExist(final Data path, final NormalizedNode data)
            throws RequestException {
        if (data instanceof NormalizedNodeContainer<?> dataNode) {
            for (final var node : dataNode.body()) {
                final var childPath = childPath(path, List.of(node.name()));
                checkItemDoesNotExists(exists(childPath), childPath);
            }
        } else {
            throw new RequestException("Unexpected node type: " + data.getClass().getName());
        }
    }

    private ListenableFuture<Boolean> exists(final Data path) {
        return Futures.transform(remapException(dataStoreService.getData(path, CONFIG_PARAM)),
            optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @paran databind the {@link DatabindContext}
     * @param path path to be checked
     * @throws RequestException if data already exists.
     */
    private static void checkItemDoesNotExists(final ListenableFuture<Boolean> existsFuture, final Data path)
            throws RequestException {
        if (RestconfStrategy.syncAccess(existsFuture, path.instance())) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                path.toErrorPath());
        }
    }

    private static NetconfDocumentedException getNetconfDocumentedException(
        final Collection<? extends RpcError> errors) {
        var errType = ErrorType.APPLICATION;
        var errSeverity = ErrorSeverity.ERROR;
        final var msgBuilder = new StringJoiner(" ");
        var errorTag = ErrorTag.OPERATION_FAILED;
        for (final RpcError error : errors) {
            errType = error.getErrorType();
            errSeverity = error.getSeverity();
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }
        return new NetconfDocumentedException("RPC during tx failed. " + msgBuilder, errType, errorTag, errSeverity);
    }


    private static boolean containsFailure(final List<PatchStatusEntity> statusEntities) {
        return statusEntities.stream().anyMatch(t -> !t.isOk());
    }

    private static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    private static <T> ListenableFuture<T> remapException(final ListenableFuture<T> input) {
        final var ret = SettableFuture.<T>create();
        Futures.addCallback(input, new FutureCallback<>() {
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
        return ret;
    }

    private static ListenableFuture<? extends DOMRpcResult> chainPatchEditTransaction(
        final ListenableFuture<? extends DOMRpcResult> futureChain,
        final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture,
        final PatchEntity previousEntity, final List<PatchStatusEntity> editCollection) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    final var requestErrors = result.errors().stream()
                        .map(t -> RequestError.ofRpcError(t, previousEntity.getDataPath().toErrorPath()))
                        .toList();
                    editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), false, requestErrors));
                    final var requestException = new RequestException(requestErrors, null, "Failed PatchRequest");
                    return Futures.immediateFailedFuture(requestException);
                }
                editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), true, null));
                return nextFuture.get();
            }, Executors.newSingleThreadExecutor());
    }

    private static ListenableFuture<? extends DOMRpcResult> addIntoFutureChain(
            final ListenableFuture<? extends DOMRpcResult> futureChain,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                return nextFuture.get();
            }, Executors.newSingleThreadExecutor());
    }

    private static @NonNull RequestException decodeException(final Throwable ex, final String txType,
            final Data dataPath) {
        if (ex instanceof RequestException requestException) {
            LOG.trace("Operation via Restconf transaction {} at path {} was not executed because of: {}",
                txType, dataPath.instance(), requestException.getMessage());
            return requestException;
        }
        if (ex instanceof NetconfDocumentedException netconfError) {
            return new RequestException(netconfError.getErrorType(), netconfError.getErrorTag(),
                netconfError.getMessage(), dataPath.toErrorPath(), ex);
        }
        return new RequestException("Transaction(" + txType + ") failed", ex);
    }

    private static void checkListAndOrderedType(final Data path) throws RequestException {
        final var dataSchemaNode = path.schema().dataSchemaNode();
        if (dataSchemaNode instanceof ListSchemaNode listSchema) {
            if (!listSchema.isUserOrdered()) {
                throw new RequestException(ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
                    "Insert parameter can be used only with ordered-by user list.");
            }
            // Verified ordered-by ListSchemaNode.
        } else if (dataSchemaNode instanceof LeafListSchemaNode leafListSchema) {
            if (!leafListSchema.isUserOrdered()) {
                throw new RequestException(ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
                    "Insert parameter can be used only with ordered-by user leaf-list.");
            }
            // Verified ordered-by LeafListSchemaNode.
        } else {
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
                "Insert parameter can be used only with list or leaf-list");
        }
    }
}
