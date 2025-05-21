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
import static org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes.fromInstanceId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.mdsal.spi.data.RestconfStrategy;
import org.opendaylight.restconf.mdsal.spi.util.ServerDataOperationsUtil;
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
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class NetconfDataOperations extends AbstractServerDataOperations {
    private static final class Candidate extends NetconfDataOperations {
        Candidate(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean lockDatastore) {
            super(id, netconfOps, rollbackSupport, lockDatastore);
        }

        /**
         * This has to be non blocking since it is called from a callback on commit and it is netty threadpool that is
         * really sensitive to blocking calls.
         */
        @Override
        ListenableFuture<? extends DOMRpcResult> discardChanges() {
            return netconfOps.discardChanges(new NetconfRpcFutureCallback("Discard candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockImpl() {
            return netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> unlockImpl() {
            return netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> commit() {
            return netconfOps.commit(new NetconfRpcFutureCallback("Commit", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild editStructure,
            final EffectiveOperation defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
            return defaultOperation == null ? netconfOps.editConfigCandidate(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigCandidate(callback, editStructure, defaultOperation, rollbackSupport);
        }
    }

    private static final class Running extends NetconfDataOperations {
        Running(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean lockDatastore) {
            super(id, netconfOps, rollbackSupport, lockDatastore);
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> discardChanges() {
            // Changes cannot be discarded from running
            return RPC_SUCCESS;
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> commit() {
            // No candidate, hence we commit immediately
            return RPC_SUCCESS;
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockImpl() {
            return netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> unlockImpl() {
            return netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild editStructure,
            final EffectiveOperation defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit running", id);
            return defaultOperation == null ? netconfOps.editConfigRunning(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigRunning(callback, editStructure, defaultOperation, rollbackSupport);
        }
    }

    private static final class CandidateWithRunning extends NetconfDataOperations {
        private final Candidate candidate;
        private final Running running;

        CandidateWithRunning(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
            final boolean rollbackSupport, final boolean lockDatastore) {
            super(id, netconfOps, rollbackSupport, lockDatastore);
            candidate = new Candidate(id, netconfOps, rollbackSupport, lockDatastore);
            running = new Running(id, netconfOps, rollbackSupport, lockDatastore);
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> discardChanges() {
            return candidate.discardChanges();
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockImpl() {
            return mergeFutures(List.of(running.lockImpl(), candidate.lockImpl()));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> unlockImpl() {
            return mergeFutures(List.of(running.unlockImpl(), candidate.unlockImpl()));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> commit() {
            return candidate.commit();
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild editStructure,
            final EffectiveOperation defaultOperation) {
            return candidate.editConfig(editStructure, defaultOperation);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataOperations.class);
    private static final ListenableFuture<DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());
    private static final @NonNull DataPutResult PUT_CREATED = new DataPutResult(true);
    private static final @NonNull DataPutResult PUT_REPLACED = new DataPutResult(false);
    private static final @NonNull DataPatchResult PATCH_EMPTY = new DataPatchResult();

    final NetconfBaseOps netconfOps;
    final RemoteDeviceId id;
    final boolean rollbackSupport;
    final boolean lockDatastore;

    private final Map<Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();

    private NetconfDataOperations(final RemoteDeviceId id, final NetconfBaseOps dataTreeService,
            final boolean rollbackSupport, final boolean lockDatastore) {
        this.netconfOps = dataTreeService;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.lockDatastore = lockDatastore;
    }

    public static @NonNull NetconfDataOperations of(final RemoteDeviceId id, final DatabindContext databind,
        final Rpcs rpcs, final NetconfSessionPreferences sessionPreferences, final boolean lockDatastore) {
        final var netconfOps = new NetconfBaseOps(databind, rpcs);
        final var rollbackSupport = sessionPreferences.isRollbackSupported();

        // Examine preferences and decide which implementation to use
        if (sessionPreferences.isCandidateSupported()) {
            return sessionPreferences.isRunningWritable()
                ? new CandidateWithRunning(id, netconfOps, rollbackSupport, lockDatastore)
                : new Candidate(id, netconfOps, rollbackSupport, lockDatastore);
        } else if (sessionPreferences.isRunningWritable()) {
            return new Running(id, netconfOps, rollbackSupport, lockDatastore);
        } else {
            throw new IllegalArgumentException("Device " + id.name() + " has advertised neither :writable-running nor "
                + ":candidate capability. Failed to establish session, as at least one of these must be advertised.");
        }
    }

    abstract ListenableFuture<? extends DOMRpcResult> discardChanges();

    abstract ListenableFuture<? extends DOMRpcResult> lockImpl();

    abstract ListenableFuture<? extends DOMRpcResult> unlockImpl();

    abstract ListenableFuture<? extends DOMRpcResult> commit();

    abstract ListenableFuture<? extends DOMRpcResult> editConfig(DataContainerChild editStructure,
        EffectiveOperation defaultOperation);

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.of(data),
            Optional.of(EffectiveOperation.CREATE), path.instance());

        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        // FIXME: Get defaultOperation
        chainFuture = addIntoFeatureChain(chainFuture, () -> editConfig(editConfigStructure, null));
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
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
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "POST", path));
            }
        }, Executors.newSingleThreadExecutor());
    }

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final Insert insert, final NormalizedNode data) {
        try {
            ServerDataOperationsUtil.checkListAndOrderedType(path);
        } catch (RequestException cause) {
            request.completeWith(cause);
            return;
        }
        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        chainFuture = addIntoFeatureChain(chainFuture, () -> {
            try {
                return insertCreate(path, data, insert);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
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
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "POST", path));
            }
        }, Executors.newSingleThreadExecutor());
    }


    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        chainFuture = addIntoFeatureChain(chainFuture, () -> {
            try {
                return delete(path);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "DELETE", path));
            }
        }, MoreExecutors.directExecutor());

    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        try {
            ServerDataOperationsUtil.syncAccess(lock(), path.instance());
            final var node = readData(path, params);
            ServerDataOperationsUtil.completeDataGET(request, node, path, NormalizedNodeWriterFactory.of(
                params.depth()), null);
            ServerDataOperationsUtil.syncAccess(unlock(), path.instance());
        } catch (RequestException e) {
            request.completeWith(e);
        }
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final Data path, final NormalizedNode data) {
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
            Optional.of(EffectiveOperation.MERGE), path.instance());

        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        chainFuture = addIntoFeatureChain(chainFuture, () -> editConfig(editConfigStructure, null));
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                // TODO: extract details once CommitInfo can communicate them
                request.completeWith(PATCH_EMPTY);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<? extends DOMRpcResult> patchRequest(final Data path, final NormalizedNode data,
            final Operation operation) throws RequestException {
        return switch (operation) {
            case Create -> {
                final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
                    Optional.of(EffectiveOperation.CREATE), path.instance());
                yield editConfig(editConfigStructure, null);
            }
            case Merge -> {
                final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
                    Optional.of(EffectiveOperation.MERGE), path.instance());
                yield editConfig(editConfigStructure, null);
            }
            case Replace -> replace(path, data);
            case Delete -> delete(path);
            case Remove -> remove(path);
            default -> Futures.immediateFailedFuture(new RequestException(ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED, "Not supported Yang Patch operation"));
        };
    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchContext patch) {
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var entities = patch.entities();
        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();

        for (int i = 0; i < entities.size() ; i++) {
            final var currentEntity = entities.get(i);
            final var targetNode = currentEntity.getDataPath();
            final var previousEntity = i > 0 ? entities.get(i) : null;
            Futures.transformAsync(chainFuture,
                result -> {
                    if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                        if (previousEntity != null) {
                            final var requestErrors = result.errors().stream()
                                .map(t -> RequestError.ofRpcError(t, previousEntity.getDataPath().toErrorPath()))
                                .toList();
                            editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), false, requestErrors));
                            final var requestException = new RequestException(requestErrors, null,
                                "Failed PatchRequest");
                            return Futures.immediateFailedFuture(requestException);
                        } else {
                            return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                        }
                    }
                    if(previousEntity != null) {
                        editCollection.add(new PatchStatusEntity(previousEntity.getEditId(), true, null));
                    }
                    return patchRequest(targetNode, currentEntity.getNode(), currentEntity.getOperation());
                }, Executors.newSingleThreadExecutor());
        }

        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
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

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final NormalizedNode data) {
        final Boolean exists;
        try {
            exists = ServerDataOperationsUtil.syncAccess(exists(path), path.instance());
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        chainFuture = addIntoFeatureChain(chainFuture, () -> replace(path, data));
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "PUT", path));
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
            exists = ServerDataOperationsUtil.syncAccess(exists(path), instance);
            parentPath = ServerDataOperationsUtil.getConceptualParent(path);
            ServerDataOperationsUtil.checkListAndOrderedType(parentPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        ListenableFuture<? extends DOMRpcResult> chainFuture = lock();
        chainFuture = addIntoFeatureChain(chainFuture, () -> {
            try {
                return insertPut(path, data, insert, parentPath);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
        });
        chainFuture = addIntoFeatureChain(chainFuture, this::commit);
        chainFuture = addIntoFeatureChain(chainFuture, this::unlock);

        Futures.addCallback(chainFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                request.completeWith(exists ? PUT_REPLACED : PUT_CREATED);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(ServerDataOperationsUtil.decodeException(cause, "PUT", path));
            }
        }, MoreExecutors.directExecutor());
    }


    private ListenableFuture<Optional<NormalizedNode>> get(final Data path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path.instance()));
    }

    private ListenableFuture<Optional<NormalizedNode>> get(final Data path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path.instance()),
            fields);
    }

    private ListenableFuture<Optional<NormalizedNode>> getConfig(final Data path) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.of(path.instance()));
    }

    private ListenableFuture<Optional<NormalizedNode>> getConfig(final Data path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.of(path.instance()), fields);
    }

    private ListenableFuture<? extends DOMRpcResult> lock() {
        if (!lockDatastore) {
            LOG.trace("Lock is not allowed by device configuration, ignoring lock results: {}", id);
            return RPC_SUCCESS;
        }
        return lockImpl();
    }

    private ListenableFuture<? extends DOMRpcResult> unlock() {
        if (!lockDatastore) {
            LOG.trace("Unlock is not allowed by device configuration, ignoring unlock results: {}", id);
            return RPC_SUCCESS;
        }
        return unlockImpl();
    }

    private ListenableFuture<? extends DOMRpcResult> remove(final Data path) throws RequestException {
        return eraseData(path, EffectiveOperation.REMOVE);
    }

    private ListenableFuture<? extends DOMRpcResult> delete(final Data path) throws RequestException {
        return eraseData(path, EffectiveOperation.DELETE);
    }

    private ListenableFuture<? extends DOMRpcResult> eraseData(final Data path, final EffectiveOperation operation)
            throws RequestException {
        final var schemaNode = path.schema().dataSchemaNode();
        final var lastPathArgument = path.instance().getLastPathArgument();
        if (lastPathArgument instanceof NodeIdentifier && (schemaNode instanceof ListSchemaNode
            || schemaNode instanceof LeafListSchemaNode)) {
            final var items = getListItemsForRemove(path);
            if (items.isEmpty()) {
                LOG.debug("Path {} contains no items, delete operation omitted.", path);
                return RPC_SUCCESS;
            } else {
                ListenableFuture<? extends DOMRpcResult> chainFeature = RPC_SUCCESS;
                for (final var childElement : items) {
                    final var childPath = childPath(path, List.of(childElement.name()));
                    final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.empty(),
                        Optional.of(operation), childPath.instance());
                    chainFeature = addIntoFeatureChain(chainFeature, () -> editConfig(editConfigStructure, null));
                }
                return chainFeature;
            }
        }
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.empty(),
            Optional.of(operation), path.instance());
        return editConfig(editConfigStructure, null);
    }

    public ListenableFuture<? extends DOMRpcResult> replace(final Data path, final NormalizedNode data) {
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
            Optional.of(EffectiveOperation.REPLACE), path.instance());
        return editConfig(editConfigStructure, null);
    }

    private ListenableFuture<? extends DOMRpcResult> addIntoFeatureChain(
            final ListenableFuture<? extends DOMRpcResult> chainFeature,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFeature) {
        return Futures.transformAsync(chainFeature,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                return nextFeature.get();
            }, Executors.newSingleThreadExecutor());
    }

    private @NonNull Collection<? extends NormalizedNode> getListItemsForRemove(final Data path)
            throws RequestException {
        final var cached = readListCache.remove(path);
        if (cached != null) {
            return cached;
        }
        final ListenableFuture<Optional<NormalizedNode>> future;
        // check if keys only can be filtered out to minimize amount of data retrieved
        if (path.schema().dataSchemaNode() instanceof ListSchemaNode listSchemaNode) {
            final var keyFields = listSchemaNode.getKeyDefinition().stream().map(YangInstanceIdentifier::of).toList();
            final var child = NodeIdentifierWithPredicates.of(path.instance().getLastPathArgument().getNodeType());
            final var childPath = childPath(path, List.of(child));
            future = getConfig(childPath, keyFields);
        } else {
            future = getConfig(path);
        }

        final var retrieved = ServerDataOperationsUtil.syncAccess(future, path.instance());
        return retrieved.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of());
    }

    private ListenableFuture<? extends DOMRpcResult> insertPut(final Data path, final NormalizedNode data,
            final @NonNull Insert insert, final Data parentPath) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
                        Optional.of(EffectiveOperation.REPLACE), path.instance());
                    yield editConfig(editConfigStructure, null);
                }
                var futureChain = remove(parentPath);
                futureChain = addIntoFeatureChain(futureChain, () -> replace(path, data));
                yield addIntoFeatureChain(futureChain, () -> replace(parentPath, readData));
            }
            case LAST -> {
                final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.of(data),
                    Optional.of(EffectiveOperation.REPLACE), path.instance());
                yield editConfig(editConfigStructure, null);
            }
            case BEFORE -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
                        Optional.of(EffectiveOperation.REPLACE), path.instance());
                    yield editConfig(editConfigStructure, null);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()), readData, true);
            }
            case AFTER -> {
                final var readData = readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.of(data),
                        Optional.of(EffectiveOperation.REPLACE), path.instance());
                    yield editConfig(editConfigStructure, null);
                }
                yield insertWithPointPut(path, parentPath, data, verifyNotNull(insert.pointArg()), readData, false);
            }
        };
    }

    private ListenableFuture<? extends DOMRpcResult> insertWithPointPut(final Data path, final Data parentPath,
            final NormalizedNode data, final @NonNull PathArgument pointArg, final NormalizedNodeContainer<?> readList,
            final boolean before) throws RequestException {
        ListenableFuture<? extends DOMRpcResult> chainFuture = remove(parentPath);

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
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(emptySubtree),
            Optional.of(EffectiveOperation.MERGE), YangInstanceIdentifier.of(emptySubtree.name()));
        chainFuture = addIntoFeatureChain(chainFuture, () -> editConfig(editConfigStructure, null));

        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                chainFuture = addIntoFeatureChain(chainFuture, () -> replace(path, data));
            }
            final var conceptualParent = ServerDataOperationsUtil.getConceptualParent(path);
            final var conChild = childPath(conceptualParent, List.of(nodeChild.name()));
            chainFuture = addIntoFeatureChain(chainFuture, () -> replace(conChild, nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                chainFuture = addIntoFeatureChain(chainFuture, () -> replace(path, data));
            }
        }
        return chainFuture;
    }

    private ListenableFuture<? extends DOMRpcResult> insertCreate(final Data path, final NormalizedNode data,
            final @NonNull Insert insert) throws RequestException {
        return switch (insert.insert()) {
            case FIRST -> {
                final var readData = readList(path);
                if (readData == null || readData.isEmpty()) {
                    final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.ofNullable(data),
                        Optional.of(EffectiveOperation.REPLACE), path.instance());
                    // FIXME: Get defaultOperation
                    yield editConfig(editConfigStructure, null);
                }
                checkListDataDoesNotExist(path, data);
                var futureChain = remove(path);
                futureChain = addIntoFeatureChain(futureChain, () -> replace(path, data));
                yield addIntoFeatureChain(futureChain, () -> replace(path, readData));
            }
            case LAST -> {
                final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.of(data),
                    Optional.of(EffectiveOperation.CREATE), path.instance());
                yield editConfig(editConfigStructure, null);
            }
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
        ListenableFuture<? extends DOMRpcResult> futureChain = remove(path);

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
                futureChain = addIntoFeatureChain(futureChain, () -> replace(path, data));
            }
            final var childPath = childPath(path, List.of(nodeChild.name()));
            futureChain = addIntoFeatureChain(futureChain, () -> replace(childPath, nodeChild));
            lastInsertedPosition++;
        }

        // In case we are inserting after last element
        if (!before) {
            if (lastInsertedPosition == lastItemPosition) {
                futureChain = addIntoFeatureChain(futureChain, () -> replace(path, data));
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

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @paran databind the {@link DatabindContext}
     * @param path path to be checked
     * @throws RequestException if data already exists.
     */
    static void checkItemDoesNotExists(final ListenableFuture<Boolean> existsFuture, final Data path)
            throws RequestException {
        if (ServerDataOperationsUtil.syncAccess(existsFuture, path.instance())) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                path.toErrorPath());
        }
    }

    protected @Nullable NormalizedNodeContainer<?> readList(final Data path) throws RequestException {
        // reading list is mainly invoked for subsequent removal,
        // cache data to avoid extra read invocation on delete/remove
        final var result =  ServerDataOperationsUtil.syncAccess(read(LogicalDatastoreType.CONFIGURATION, path),
            path.instance());
        readListCache.put(path, result.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of()));
        return (NormalizedNodeContainer<?>) result.orElse(null);
    }

    private static NetconfDocumentedException getNetconfDocumentedException(
        final Collection<? extends RpcError> errors) {
        ErrorType errType = ErrorType.APPLICATION;
        ErrorSeverity errSeverity = ErrorSeverity.ERROR;
        StringJoiner msgBuilder = new StringJoiner(" ");
        ErrorTag errorTag = ErrorTag.OPERATION_FAILED;
        for (final RpcError error : errors) {
            errType = error.getErrorType();
            errSeverity = error.getSeverity();
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }
        return new NetconfDocumentedException("RPC during tx failed. " + msgBuilder, errType, errorTag, errSeverity);
    }


    private static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    // Transform list of futures related to RPC operation into a single Future
    private static ListenableFuture<DOMRpcResult> mergeFutures(
            final List<ListenableFuture<? extends DOMRpcResult>> futures) {
        return Futures.whenAllComplete(futures).call(() -> {
            if (futures.size() == 1) {
                // Fast path
                return Futures.getDone(futures.get(0));
            }

            final var builder = ImmutableList.<RpcError>builder();
            for (ListenableFuture<? extends DOMRpcResult> future : futures) {
                builder.addAll(Futures.getDone(future).errors());
            }
            return new DefaultDOMRpcResult(null, builder.build());
        }, MoreExecutors.directExecutor());
    }

    protected ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path);
            case OPERATIONAL -> get(path);
        };
    }

    private ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path,
            final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path, fields);
            case OPERATIONAL -> get(path, fields);
        };
    }

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param path     the parent path to read
     * @param params value of with-defaults parameter
     * @return {@link NormalizedNode}
     * @throws RequestException when an error occurs
     */
    // FIXME: NETCONF-1155: this method should asynchronous
    public @Nullable NormalizedNode readData(final @NonNull Data path, final @NonNull DataGetParams params)
            throws RequestException {
        //  type of data to read (config, state, all)
        final var content = params.content();
        // value of with-defaults parameter
        final var withDefaults = params.withDefaults();
        final var fields = params.fields();
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);

                yield ServerDataOperationsUtil.mergeConfigAndSTateDataIfNeeded(stateDataNode, withDefaults == null
                    ? configDataNode : ServerDataOperationsUtil.prepareDataByParamWithDef(configDataNode, path,
                    withDefaults.mode()));
            }
            case CONFIG -> {
                final var read = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);
                yield withDefaults == null ? read : ServerDataOperationsUtil.prepareDataByParamWithDef(read, path,
                    withDefaults.mode());
            }
            case NONCONFIG -> readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
        };
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link RestconfStrategy} with
     * specified subtrees that should only be read.
     *
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param fields                paths to selected subtrees which should be read, relative to the parent path
     * @return {@link NormalizedNode}
     * @throws RequestException when an error occurs
     */
    private @Nullable NormalizedNode readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final @NonNull Data path, final FieldsParam fields) throws RequestException {
        // Paths to selected subtrees which should be read, relative to the parent path
        final List<YangInstanceIdentifier> fieldPaths;
        if (fields != null) {
            final var tmp = fieldsParamToPaths(path.inference().modelContext(), path.schema(), fields);
            fieldPaths = tmp.isEmpty() ? null : tmp;
        } else {
            fieldPaths = null;
        }

        if (fieldPaths != null) {
            return ServerDataOperationsUtil.syncAccess(read(store, path, fieldPaths), path.instance()).orElse(null);
        }
        return ServerDataOperationsUtil.syncAccess(read(store, path), path.instance()).orElse(null);
    }

    protected ListenableFuture<Boolean> exists(final Data path) {
        return Futures.transform(remapException(getConfig(path)),
            optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
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

    /**
     * Translate a {@link FieldsParam} to a list of child node paths saved in lists, suitable for use with
     * {@link NetconfDataTreeService}.
     *
     * <p>Fields parser that stores a set of all the leaf {@link LinkedPathElement}s specified in {@link FieldsParam}.
     * Using {@link LinkedPathElement} it is possible to create a chain of path arguments and build complete paths
     * since this element contains identifiers of intermediary mixin nodes and also linked to its parent
     * {@link LinkedPathElement}.
     *
     * <p>Example: field 'a(b/c;d/e)' ('e' is place under choice node 'x') is parsed into following levels:
     * <pre>
     *   - './a' +- 'a/b' - 'b/c'
     *           |
     *           +- 'a/d' - 'd/x/e'
     * </pre>
     *
     *
     * @param modelContext EffectiveModelContext
     * @param startNode Root DataSchemaNode
     * @param input input value of fields parameter
     * @return {@link List} of {@link YangInstanceIdentifier} that are relative to the last {@link PathArgument}
     *         of provided {@code identifier}
     * @throws RequestException when an error occurs
     */
    @VisibleForTesting
    public static @NonNull List<YangInstanceIdentifier> fieldsParamToPaths(
            final @NonNull EffectiveModelContext modelContext, final @NonNull DataSchemaContext startNode,
            final @NonNull FieldsParam input) throws RequestException {
        final var parsed = new HashSet<LinkedPathElement>();
        processSelectors(parsed, modelContext, startNode.dataSchemaNode().getQName().getModule(),
            new LinkedPathElement(null, List.of(), startNode), input.nodeSelectors());
        return parsed.stream().map(NetconfDataOperations::buildPath).toList();
    }

    private static void processSelectors(final Set<LinkedPathElement> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final LinkedPathElement startPathElement,
            final List<FieldsParam.NodeSelector> selectors) throws RequestException {
        for (var selector : selectors) {
            var pathElement = startPathElement;
            var namespace = startNamespace;

            // Note: path is guaranteed to have at least one step
            final var it = selector.path().iterator();
            do {
                final var step = it.next();
                final var module = step.module();
                if (module != null) {
                    // FIXME: this is not defensive enough, as we can fail to find the module
                    namespace = context.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed path element linked to its parent
                pathElement = addChildPathElement(pathElement, step.identifier().bindTo(namespace));
            } while (it.hasNext());

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, context, namespace, pathElement, subs);
            } else {
                parsed.add(pathElement);
            }
        }
    }

    private static LinkedPathElement addChildPathElement(final LinkedPathElement currentElement,
            final QName childQName) throws RequestException {
        final var collectedMixinNodes = new ArrayList<PathArgument>();

        final var currentNode = currentElement.targetNode;
        var actualContextNode = childByQName(currentNode, childQName);
        if (actualContextNode == null) {
            actualContextNode = resolveMixinNode(currentNode, currentNode.getPathStep().getNodeType());
            actualContextNode = childByQName(actualContextNode, childQName);
        }

        while (actualContextNode != null && actualContextNode instanceof DataSchemaContext.PathMixin) {
            final var actualDataSchemaNode = actualContextNode.dataSchemaNode();
            if (actualDataSchemaNode instanceof ListSchemaNode listSchema && listSchema.getKeyDefinition().isEmpty()) {
                // we need just a single node identifier from list in the path IFF it is an unkeyed list, otherwise
                // we need both (which is the default case)
                actualContextNode = childByQName(actualContextNode, childQName);
            } else if (actualDataSchemaNode instanceof LeafListSchemaNode) {
                // NodeWithValue is unusable - stop parsing
                break;
            } else {
                collectedMixinNodes.add(actualContextNode.getPathStep());
                actualContextNode = childByQName(actualContextNode, childQName);
            }
        }

        if (actualContextNode == null) {
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, "Child %s node missing in %s",
                childQName.getLocalName(), currentNode.getPathStep().getNodeType().getLocalName());
        }

        return new LinkedPathElement(currentElement, collectedMixinNodes, actualContextNode);
    }

    private static @Nullable DataSchemaContext childByQName(final DataSchemaContext parent, final QName qname) {
        return parent instanceof DataSchemaContext.Composite composite ? composite.childByQName(qname) : null;
    }

    private static YangInstanceIdentifier buildPath(final LinkedPathElement lastPathElement) {
        var pathElement = lastPathElement;
        final var path = new LinkedList<PathArgument>();
        do {
            path.addFirst(contextPathArgument(pathElement.targetNode));
            path.addAll(0, pathElement.mixinNodesToTarget);
            pathElement = pathElement.parentPathElement;
        } while (pathElement.parentPathElement != null);

        return YangInstanceIdentifier.of(path);
    }

    private static @NonNull PathArgument contextPathArgument(final DataSchemaContext context) {
        final var arg = context.pathStep();
        if (arg != null) {
            return arg;
        }

        final var schema = context.dataSchemaNode();
        if (schema instanceof ListSchemaNode listSchema && !listSchema.getKeyDefinition().isEmpty()) {
            return NodeIdentifierWithPredicates.of(listSchema.getQName());
        }
        if (schema instanceof LeafListSchemaNode leafListSchema) {
            return new YangInstanceIdentifier.NodeWithValue<>(leafListSchema.getQName(), Empty.value());
        }
        throw new UnsupportedOperationException("Unsupported schema " + schema);
    }

    private static DataSchemaContext resolveMixinNode(final DataSchemaContext node,
            final @NonNull QName qualifiedName) {
        DataSchemaContext currentNode = node;
        while (currentNode != null && currentNode instanceof DataSchemaContext.PathMixin currentMixin) {
            currentNode = currentMixin.childByQName(qualifiedName);
        }
        return currentNode;
    }

    /**
     * {@link DataSchemaContext} of data element grouped with identifiers of leading mixin nodes and previous path
     * element.<br>
     *  - identifiers of mixin nodes on the path to the target node - required for construction of full valid
     *    DOM paths,<br>
     *  - {@link LinkedPathElement} of the previous non-mixin node - required to successfully create a chain
     *    of {@link PathArgument}s
     *
     * @param parentPathElement     parent path element
     * @param mixinNodesToTarget    identifiers of mixin nodes on the path to the target node
     * @param targetNode            target non-mixin node
     */
    private record LinkedPathElement(
        @Nullable LinkedPathElement parentPathElement,
        @NonNull List<PathArgument> mixinNodesToTarget,
        @NonNull DataSchemaContext targetNode) {
        LinkedPathElement {
            requireNonNull(mixinNodesToTarget);
            requireNonNull(targetNode);
        }
    }
}
