/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.QueryParameters;
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

/**
 *  Implementation of RESTCONF datastore resource, as specified by
 *  <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC8040 {+restconf}/data</a>,
 *  for communication with a NETCONF endpoint.
 */
public final class NetconfDataOperations extends AbstractServerDataOperations {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataOperations.class);
    private static final @NonNull DataPutResult PUT_CREATED = new DataPutResult(true);
    private static final @NonNull DataPutResult PUT_REPLACED = new DataPutResult(false);
    private static final @NonNull DataPatchResult PATCH_EMPTY = new DataPatchResult();
    private static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());
    private static final DataGetParams CONFIG_PARAM = DataGetParams.of(QueryParameters.of(ContentParam.CONFIG));

    private final DataOperationService dataStoreService;

    public NetconfDataOperations(final @NonNull DataOperationService dataStoreService) {
        this.dataStoreService = requireNonNull(dataStoreService);
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        LOG.debug("Execute CREATE operation with data {} and path {}", data, path);
        var futureChain = dataStoreService.createData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful CREATE operation with data {}, path {} and result {}", data, path, result);
                final ApiPath apiPath;
                try {
                    apiPath = new ApiPathCanonizer(path.databind()).dataToApiPath(
                        data.body() instanceof MapNode mapData && !mapData.isEmpty()
                            ? path.instance().node(mapData.body().iterator().next().name()) : path.instance());
                } catch (RequestException e) {
                    // This should never happen
                    request.failWith(e);
                    return;
                }
                request.completeWith(new CreateResourceResult(apiPath));
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed CREATE operation with data {} and path {}", data, path, cause);
                request.failWith(decodeException(cause, "POST", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final Insert insert, final NormalizedNode data) {
        LOG.debug("Execute CREATE operation with {} INSERT query, data {} and path {}", insert, data, path);
        try {
            checkListAndOrderedType(path);
        } catch (RequestException cause) {
            request.failWith(cause);
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
                LOG.debug("Successful CREATE operation with {} INSERT query, data {}, path {} and result {}",
                    insert, data, path, result);
                final ApiPath apiPath;
                try {
                    apiPath = new ApiPathCanonizer(path.databind()).dataToApiPath(
                        data.body() instanceof MapNode mapData && !mapData.isEmpty()
                            ? path.instance().node(mapData.body().iterator().next().name()) : path.instance());
                } catch (RequestException e) {
                    // This should never happen
                    request.failWith(e);
                    return;
                }
                request.completeWith(new CreateResourceResult(apiPath));
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed CREATE operation with {} INSERT query, data {} and path {}", insert, data, path,
                    cause);
                request.failWith(decodeException(cause, "POST", path));
            }
        }, MoreExecutors.directExecutor());
    }


    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        LOG.debug("Execute DELETE operation on path {}", path);
        var futureChain = dataStoreService.deleteData(path);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful DELETE operation on path {} with result {}", path, result);
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed DELETE operation on path {}", path, cause);
                request.failWith(decodeException(cause, "DELETE", path));
            }
        }, MoreExecutors.directExecutor());

    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        LOG.debug("Execute GET operation with path {} and params {}", path, params);
        Futures.addCallback(dataStoreService.getData(path, params), new FutureCallback<>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode> result) {
                LOG.debug("Successful GET operation with path {} and params {}", path, params);
                // Non-existing data
                if (result.isEmpty()) {
                    request.failWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
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
                LOG.error("Failed GET operation with path {} and params {}", path, params, cause);
                request.failWith(decodeException(cause, "GET", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final Data path, final NormalizedNode data) {
        LOG.debug("Execute MERGE operation with data {} and path {}", data, path);
        var futureChain = dataStoreService.mergeData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful MERGE operation with data {}, path {} and result {}", data, path, result);
                // TODO: extract details once DOMRpcResult can communicate them
                request.completeWith(PATCH_EMPTY);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed MERGE operation with data {} and path {}", data, path, cause);
                request.failWith(decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchContext patch) {
        LOG.debug("Execute PATCH operation with path {} and PatchContext {}", path, patch);
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var entities = patch.entities();
        final var iterator = patch.entities().iterator();

        ListenableFuture<? extends DOMRpcResult> futureChain;
        if (entities.isEmpty()) {
            futureChain = dataStoreService.commit();
        } else {
            final var firstEntity = iterator.next();
            final var firstTargetNode = firstEntity.getDataPath();
            futureChain = patchRequest(firstTargetNode, firstEntity.getNode(), firstEntity.getOperation());

            int i = 0;
            while (iterator.hasNext()) {
                i++;
                final var currentEntity = iterator.next();
                final var targetNode = currentEntity.getDataPath();
                final var previousEntity = i > 0 ? entities.get(i - 1) : null;

                futureChain = chainPatchEditTransaction(futureChain,
                    () -> patchRequest(targetNode, currentEntity.getNode(), currentEntity.getOperation()),
                    previousEntity, editCollection);
            }

            futureChain = chainPatchEditTransaction(futureChain, dataStoreService::commit,
                entities.get(entities.size() - 1), editCollection);
        }

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful PATCH operation with path {}, PatchContext {} and result {}", path, patch,
                    result);
                request.completeWith(new DataYangPatchResult(
                    new PatchStatusContext(patch.patchId(), List.copyOf(editCollection), true, null)));
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed PATCH operation with path {} and PatchContext {}", path, patch, cause);
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
        LOG.debug("Execute PUT operation with data {} and path {}", data, path);
        final Boolean exists;
        try {
            exists = RestconfStrategy.syncAccess(exists(path), path.instance());
        } catch (RequestException e) {
            request.failWith(e);
            return;
        }
        var futureChain = dataStoreService.putData(path, data);
        futureChain = addIntoFutureChain(futureChain, dataStoreService::commit);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful PUT operation with data {}, path {} and result {}", data, path, result);
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed PUT operation with data {} and path {}", data, path, cause);
                request.failWith(decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final Insert insert,
            final NormalizedNode data) {
        LOG.debug("Execute PUT operation with {} INSERT query, data {} and path {}", insert, data, path);
        final var instance = path.instance();
        final var parentInstance = instance.coerceParent();
        final var parentPath = path.enterPath(parentInstance);
        final Boolean exists;
        try {
            exists = RestconfStrategy.syncAccess(exists(path), instance);
            checkListAndOrderedType(parentPath);
        } catch (RequestException e) {
            request.failWith(e);
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
                LOG.debug("Successful PUT operation with {} INSERT query, data {}, path {} and result {}", insert,
                    data, path, result);
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed PUT operation with {} INSERT query, data {} and path {}", insert, data, path);
                request.failWith(decodeException(cause, "PUT", path));
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
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(parentPath, CONFIG_PARAM),
                    parentPath.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                var futureChain = dataStoreService.removeData(parentPath);
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
                yield addIntoFutureChain(futureChain, () -> dataStoreService.putData(parentPath,
                    readData.orElseThrow()));
            }
            case LAST -> dataStoreService.putData(path, data);
            case BEFORE -> {
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(parentPath, CONFIG_PARAM),
                    parentPath.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()),
                    (NormalizedNodeContainer<?>) readData.orElseThrow(), true);
            }
            case AFTER -> {
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(parentPath, CONFIG_PARAM),
                    parentPath.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()),
                    (NormalizedNodeContainer<?>) readData.orElseThrow(), false);
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPut(final Data path, final Data parentPath,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) {
        var futureChain = dataStoreService.removeData(parentPath);

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

        final var parentInstance = parentPath.instance();
        int lastInsertedPosition = 0;
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
            final var conChild = parentPath.enterPath(parentInstance.node(nodeChild.name()));
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
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(path, CONFIG_PARAM),
                    path.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                checkListDataDoesNotExist(path, data);
                var futureChain = dataStoreService.removeData(path);
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
                yield addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, readData.orElseThrow()));
            }
            case LAST -> dataStoreService.createData(path, data);
            case BEFORE -> {
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(path, CONFIG_PARAM),
                    path.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                checkListDataDoesNotExist(path, data);
                yield insertWithPointPost(path, data, verifyNotNull(insert.pointArg()),
                    (NormalizedNodeContainer<?>) readData.orElseThrow(), true);
            }
            case AFTER -> {
                final var readData = RestconfStrategy.syncAccess(dataStoreService.getData(path, CONFIG_PARAM),
                    path.instance());
                if (readData.isEmpty() || isEmptyContainerNode(readData.orElseThrow())) {
                    yield dataStoreService.putData(path, data);
                }
                checkListDataDoesNotExist(path, data);
                yield insertWithPointPost(path, data, verifyNotNull(insert.pointArg()),
                    (NormalizedNodeContainer<?>) readData.orElseThrow(), false);
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
        final var instance = path.instance();
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.putData(path, data));
            }
            final var childPath = path.enterPath(instance.node(nodeChild.name()));
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
    private void checkListDataDoesNotExist(final Data path, final NormalizedNode data) throws RequestException {
        if (data instanceof NormalizedNodeContainer<?> dataNode) {
            final var instance = path.instance();
            for (final var child : dataNode.body()) {
                final var childPath = path.enterPath(instance.node(child.name()));
                checkItemDoesNotExists(exists(childPath), childPath);
            }
        } else {
            throw new RequestException("Unexpected node type: " + data.getClass().getName());
        }
    }

    private ListenableFuture<Boolean> exists(final Data path) {
        final var remappedException = Futures.catchingAsync(dataStoreService.getData(path, CONFIG_PARAM),
            Throwable.class, cause -> {
                final var readFailedException = cause instanceof ReadFailedException readFailed ? readFailed
                    : new ReadFailedException("NETCONF GET operation failed", cause);
                return Futures.immediateFailedFuture(readFailedException);
            }, MoreExecutors.directExecutor());

        return Futures.transform(remappedException, optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
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

    private static ListenableFuture<? extends DOMRpcResult> chainPatchEditTransaction(
            final ListenableFuture<? extends DOMRpcResult> futureChain,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture,
            final PatchEntity previousEntity, final List<PatchStatusEntity> editCollection) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    LOG.trace("Failed patch operation with PatchEntity {} and result {}", previousEntity, result);
                    final var requestErrors = result.errors().stream()
                        .map(t -> RequestError.ofRpcError(t, previousEntity.getDataPath().toErrorPath()))
                        .toList();
                    editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), false, requestErrors));
                    final var requestException = new RequestException(requestErrors, null, "Failed Patch operation with"
                        + " edit-id " + previousEntity.getEditId());
                    return Futures.immediateFailedFuture(requestException);
                }
                editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), true, null));
                return nextFuture.get();
            }, MoreExecutors.directExecutor());
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
            }, MoreExecutors.directExecutor());
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

    private static boolean isEmptyContainerNode(final NormalizedNode readData) {
        return readData instanceof NormalizedNodeContainer<?> contData && contData.isEmpty();
    }
}
