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
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.mdsal.spi.data.AsyncGetDataProcessor;
import org.opendaylight.restconf.mdsal.spi.data.RestconfStrategy;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
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
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
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

    private final Map<Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();
    private final NetconfDataTreeService dataTreeService;

    public NetconfDataOperations(final @NonNull NetconfDataTreeService dataTreeService) {
        this.dataTreeService = requireNonNull(dataTreeService);
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        LOG.debug("Execute CREATE operation with data {} and path {}", data, path);
        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> create(path, data));
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

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
                cancel();
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
        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> {
            try {
                return insertCreate(path, data, insert);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

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
                cancel();
                request.failWith(decodeException(cause, "POST", path));
            }
        }, MoreExecutors.directExecutor());
    }


    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        LOG.debug("Execute DELETE operation on path {}", path);
        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> {
            try {
                return delete(path);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful DELETE operation on path {} with result {}", path, result);
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed DELETE operation on path {}", path, cause);
                cancel();
                request.failWith(decodeException(cause, "DELETE", path));
            }
        }, MoreExecutors.directExecutor());

    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        LOG.debug("Execute GET operation with path {} and params {}", path, params);
        final var netconfGetRequest = request.<Optional<NormalizedNode>>transform(result -> {
            if (result.isEmpty()) {
                throw new IllegalStateException("Request transformation could not be completed without data");
            }
            final var normalizedNode = result.orElseThrow();
            final var writerFactory = NormalizedNodeWriterFactory.of(params.depth());
            final var body = NormalizedFormattableBody.of(path, normalizedNode, writerFactory);
            return new DataGetResult(body);
        });

        try {
            readData(netconfGetRequest, path, params);
        } catch (RequestException e) {
            request.failWith(e);
        }
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final Data path, final NormalizedNode data) {
        LOG.debug("Execute MERGE operation with data {} and path {}", data, path);
        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> merge(path, data));
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

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
                cancel();
                request.failWith(decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchContext patch) {
        LOG.debug("Execute PATCH operation with path {} and PatchContext {}", path, patch);
        final var editCollection = new ArrayList<PatchStatusEntity>();
        var futureChain = dataTreeService.lock();

        for (var patchEntity : patch.entities()) {
            if (!containsFailure(editCollection)) {
                final var targetNode = patchEntity.getDataPath();
                final var editId = patchEntity.getEditId();

                switch (patchEntity.getOperation()) {
                    case Create:
                        futureChain = addIntoFutureChain(futureChain, () -> create(targetNode, patchEntity.getNode()));
                        editCollection.add(new PatchStatusEntity(editId, true, null));
                        break;
                    case Delete:
                        futureChain = addIntoFutureChain(futureChain, () -> {
                            try {
                                final var delete = delete(targetNode);
                                editCollection.add(new PatchStatusEntity(editId, true, null));
                                return delete;
                            } catch (RequestException cause) {
                                editCollection.add(new PatchStatusEntity(editId, false, cause.errors()));
                                return Futures.immediateFailedFuture(cause);
                            }
                        });
                        break;
                    case Merge:
                        futureChain = addIntoFutureChain(futureChain, () -> merge(targetNode, patchEntity.getNode()));
                        editCollection.add(new PatchStatusEntity(editId, true, null));
                        break;
                    case Replace:
                        futureChain = addIntoFutureChain(futureChain, () -> replace(targetNode, patchEntity.getNode()));
                        editCollection.add(new PatchStatusEntity(editId, true, null));
                        break;
                    case Remove:
                        futureChain = addIntoFutureChain(futureChain, () -> {
                            try {
                                final var remove = remove(targetNode);
                                editCollection.add(new PatchStatusEntity(editId, true, null));
                                return remove;
                            } catch (RequestException cause) {
                                editCollection.add(new PatchStatusEntity(editId, false, cause.errors()));
                                return Futures.immediateFailedFuture(cause);
                            }
                        });
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(editId, false, List.of(
                            new RequestError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                                "Not supported Yang Patch operation"))));
                        break;
                }
            }
        }

        if (containsFailure(editCollection)) {
            cancel();
            request.completeWith(new DataYangPatchResult(new PatchStatusContext(patch.patchId(),
                List.copyOf(editCollection), false, null)));
            return;
        }
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

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
                cancel();
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
        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

        Futures.addCallback(futureChain, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                LOG.debug("Successful PUT operation with data {}, path {} and result {}", data, path, result);
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed PUT operation with data {} and path {}", data, path, cause);
                cancel();
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

        var futureChain = dataTreeService.lock();
        futureChain = addIntoFutureChain(futureChain, () -> {
            try {
                return insertPut(path, data, insert, parentPath);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        futureChain = addIntoFutureChain(futureChain, dataTreeService::commit);
        futureChain = addIntoFutureChain(futureChain, dataTreeService::unlock);

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
                cancel();
                request.failWith(decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param getRequest        {@link ServerRequest} with {@link Optional} {@link NormalizedNode} result
     * @param path              the parent path to read
     * @param params            value of with-defaults parameter
     * @throws RequestException when an error occurs
     */
    @VisibleForTesting
    public void readData(final @NonNull ServerRequest<Optional<NormalizedNode>> getRequest, final @NonNull Data path,
            final @NonNull DataGetParams params) throws RequestException {
        //  type of data to read (config, state, all)
        final var content = params.content();
        // value of with-defaults parameter
        final var fields = params.fields();
        final var processor = new AsyncGetDataProcessor(path, params.withDefaults());
        final var getFuture = switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataFuture = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, fields);

                yield processor.all(configDataFuture, stateDataFuture);
            }
            case CONFIG -> {
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, fields);
                yield processor.config(configDataFuture);
            }
            case NONCONFIG -> processor.nonConfig(
                readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields));
        };

        Futures.addCallback(getFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Optional<NormalizedNode> result) {
                if (result.isPresent()) {
                    getRequest.completeWith(result);
                } else {
                    getRequest.failWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                        "Request could not be completed because the relevant data model content does not exist"));
                }
            }

            @Override
            public void onFailure(Throwable cause) {
                getRequest.failWith(decodeException(cause, "GET", path));
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path) {
        return switch (store) {
            case CONFIGURATION -> dataTreeService.getConfig(path.instance());
            case OPERATIONAL -> dataTreeService.get(path.instance());
        };
    }

    private ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path,
            final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> dataTreeService.getConfig(path.instance(), fields);
            case OPERATIONAL -> dataTreeService.get(path.instance(), fields);
        };
    }

    private ListenableFuture<? extends DOMRpcResult> create(final Data path, final NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var iterator = ((NormalizedNodeContainer<?>) data).body().iterator();
            final var first = (NormalizedNode) iterator.next();
            final var firstPath = path.instance().node(first.name());
            var future = dataTreeService.create(CONFIGURATION, firstPath, first, Optional.empty());
            while (iterator.hasNext()) {
                final var child = ((NormalizedNode) iterator.next());
                final var childPath = path.instance().node(child.name());
                addIntoFutureChain(future, () -> dataTreeService.replace(CONFIGURATION, childPath, child,
                    Optional.empty()));
            }
            return future;
        } else {
            return dataTreeService.create(CONFIGURATION, path.instance(), data, Optional.empty());
        }
    }

    private ListenableFuture<? extends DOMRpcResult> merge(final Data path, final NormalizedNode data) {
        return dataTreeService.merge(CONFIGURATION, path.instance(), data, Optional.empty());
    }

    private ListenableFuture<? extends DOMRpcResult> replace(final Data path, final NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var iterator = ((NormalizedNodeContainer<?>) data).body().iterator();
            final var first = (NormalizedNode) iterator.next();
            final var firstPath = path.instance().node(first.name());
            var future = dataTreeService.create(CONFIGURATION, firstPath, first, Optional.empty());
            while (iterator.hasNext()) {
                final var child = ((NormalizedNode) iterator.next());
                final var childPath = path.instance().node(child.name());
                addIntoFutureChain(future, () -> dataTreeService.replace(CONFIGURATION, childPath, child,
                    Optional.empty()));
            }
            return future;
        } else {
            return dataTreeService.replace(CONFIGURATION, path.instance(), data, Optional.empty());
        }
    }

    private ListenableFuture<? extends DOMRpcResult> delete(final Data path) throws RequestException {
        return eraseData(path, (b) -> dataTreeService.delete(CONFIGURATION, b));
    }

    private ListenableFuture<? extends DOMRpcResult> remove(final Data path) throws RequestException {
        return eraseData(path, (b) -> dataTreeService.remove(CONFIGURATION, b));
    }

    private ListenableFuture<? extends DOMRpcResult> eraseData(final Data path,
            final Function<YangInstanceIdentifier, ListenableFuture<? extends DOMRpcResult>> operation)
            throws RequestException {
        if (isListPath(path)) {
            final var items = getListItemsForRemove(path);
            if (items.isEmpty()) {
                LOG.debug("Path {} contains no items, delete operation omitted.", path);
                return Futures.immediateFuture(new DefaultDOMRpcResult());
            } else {
                final var iterator = items.iterator();
                final var first = (NormalizedNode) iterator.next();
                final var firstChildPath = path.instance().node(first.name());
                var future = operation.apply(firstChildPath);
                while (iterator.hasNext()) {
                    final var childElement = iterator.next();
                    final var childPath = path.instance().node(childElement.name());
                    future = addIntoFutureChain(future, () -> operation.apply(childPath));
                }
                return future;
            }
        }
        return operation.apply(path.instance());
    }

    private @NonNull Collection<? extends NormalizedNode> getListItemsForRemove(final Data path)
            throws RequestException {
        final var cached = readListCache.remove(path);
        if (cached != null) {
            LOG.debug("Found cached list data {} on path {}", cached, path);
            return cached;
        }
        final var instance = path.instance();
        final ListenableFuture<Optional<NormalizedNode>> future;
        // check if keys only can be filtered out to minimize amount of data retrieved
        if (path.schema().dataSchemaNode() instanceof ListSchemaNode listSchemaNode) {
            final var keyFields = listSchemaNode.getKeyDefinition().stream().map(YangInstanceIdentifier::of).toList();
            final var child = NodeIdentifierWithPredicates.of(instance.getLastPathArgument().getNodeType());
            final var childPath = instance.node(child);
            future = dataTreeService.getConfig(childPath, keyFields);
        } else {
            future = dataTreeService.getConfig(instance);
        }

        final var retrieved = RestconfStrategy.syncAccess(future, instance);
        return retrieved.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of());
    }

    private ListenableFuture<? extends DOMRpcResult> insertPut(final Data path, final NormalizedNode data,
            final @NonNull Insert insert, final Data parentPath) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                }
                var futureChain = remove(parentPath);
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
                yield addIntoFutureChain(futureChain, () -> replace(parentPath, readData));
            }
            case LAST -> replace(path, data);
            case BEFORE -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()), readData, true);
            }
            case AFTER -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()), readData, false);
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPut(final Data path, final Data parentPath,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) throws RequestException {
        var futureChain = remove(parentPath);

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
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
            }
            final var conChild = parentPath.enterPath(parentInstance.node(nodeChild.name()));
            futureChain = addIntoFutureChain(futureChain, () -> replace(conChild, nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
            }
        }
        return futureChain;
    }

    private ListenableFuture<? extends DOMRpcResult> insertCreate(final Data path, final NormalizedNode data,
            final @NonNull Insert insert) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = readList(path);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                }
                checkListDataDoesNotExist(path, data);
                var futureChain = remove(path);
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
                yield addIntoFutureChain(futureChain, () -> replace(path, readData));
            }
            case LAST -> create(path, data);
            case BEFORE -> {
                final var readData = readList(path);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                } else {
                    checkListDataDoesNotExist(path, data);
                    yield insertWithPointPost(path, data, verifyNotNull(insert.pointArg()), readData, true);
                }
            }
            case AFTER -> {
                final var readData = readList(path);
                if (readData == null || readData.isEmpty()) {
                    yield replace(path, data);
                } else {
                    checkListDataDoesNotExist(path, data);
                    yield insertWithPointPost(path, data, verifyNotNull(insert.pointArg()), readData, false);
                }
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPost(final Data path, final NormalizedNode data,
            final PathArgument pointArg, final NormalizedNodeContainer<?> readList, final boolean before)
            throws RequestException {
        var futureChain = remove(path);

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
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
            }
            final var childPath = path.enterPath(instance.node(nodeChild.name()));
            futureChain = addIntoFutureChain(futureChain, () -> replace(childPath, nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFutureChain(futureChain, () -> replace(path, data));
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

    private @Nullable NormalizedNodeContainer<?> readList(final Data path) throws RequestException {
        // reading list is mainly invoked for subsequent removal,
        // cache data to avoid extra read invocation on delete/remove
        final var result =  RestconfStrategy.syncAccess(read(CONFIGURATION, path), path.instance());
        readListCache.put(path, result.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of()));
        return (NormalizedNodeContainer<?>) result.orElse(null);
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link NetconfDataTreeService} with
     * specified subtrees that should only be read.
     *
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param fields                paths to selected subtrees which should be read, relative to the parent path
     * @return {@link NormalizedNode}
     * @throws RequestException when an error occurs
     */
    private ListenableFuture<Optional<NormalizedNode>> readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final @NonNull Data path, final FieldsParam fields) throws RequestException {
        // Paths to selected subtrees which should be read, relative to the parent path
        final var fieldPaths = FieldsParamParser.fieldsParamsToPaths(path.inference().modelContext(), path.schema(),
            fields);

        if (fieldPaths.isEmpty()) {
            return read(store, path);
        }
        return read(store, path, fieldPaths);
    }

    private ListenableFuture<Boolean> exists(final Data path) {
        final var remappedException = Futures.catchingAsync(dataTreeService.getConfig(path.instance()), Throwable.class,
            cause -> {
                final var readFailedException = cause instanceof ReadFailedException readFailed ? readFailed
                    : new ReadFailedException("NETCONF GET operation failed", cause);
                return Futures.immediateFailedFuture(readFailedException);
            }, MoreExecutors.directExecutor());

        return Futures.transform(remappedException, optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    private void cancel() {
        readListCache.clear();
        executeWithLogging(dataTreeService::discardChanges);
        executeWithLogging(dataTreeService::unlock);
    }

    private static void executeWithLogging(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        var operationResult = operation.get();
        Futures.addCallback(operationResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult rpcResult) {
                if (rpcResult != null && !rpcResult.errors().isEmpty()) {
                    LOG.error("Errors occurred during processing of the RPC operation: {}",
                        rpcResult.errors().stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Error processing operation", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private static boolean isListPath(final Data path) {
        if (path.instance().getLastPathArgument() instanceof NodeIdentifier) {
            // list can be referenced by NodeIdentifier only, prevent list item do be identified as list
            final var schemaNode = path.schema().dataSchemaNode();
            return schemaNode instanceof ListSchemaNode || schemaNode instanceof LeafListSchemaNode;
        }
        return false;
    }

    private static boolean isNonEmptyListPath(final NormalizedNode data) {
        return (data instanceof MapNode || data instanceof LeafSetNode)
            && !((NormalizedNodeContainer<?>) data).body().isEmpty();
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
}
