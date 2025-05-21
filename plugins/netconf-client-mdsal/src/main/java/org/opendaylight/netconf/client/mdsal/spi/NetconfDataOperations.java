/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
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
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.mdsal.spi.util.ServerDataOperationsUtil;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerDataOperations;
import org.opendaylight.restconf.server.spi.ApiPathCanonizer;
import org.opendaylight.restconf.server.spi.Insert;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
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
    private static final ListenableFuture<DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(new DefaultDOMRpcResult());

    final NetconfBaseOps netconfOps;
    final RemoteDeviceId id;
    final boolean rollbackSupport;
    final boolean lockDatastore;

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
        final boolean rollbackSupport = sessionPreferences.isRollbackSupported();

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
    abstract ListenableFuture<? extends DOMRpcResult> lockImpl() ;
    abstract ListenableFuture<? extends DOMRpcResult> unlockImpl() ;
    abstract ListenableFuture<? extends DOMRpcResult> commit() ;
    abstract ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild editStructure,
        final EffectiveOperation defaultOperation);

    @Override
    protected void createData(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final NormalizedNode data) {
        final var editConfigStructure = netconfOps.createEditConfigStructure(Optional.of(data),
            Optional.of(EffectiveOperation.CREATE), path.instance());

        ListenableFuture<? extends DOMRpcResult> chainFeature = lock();
        chainFeature = Futures.transformAsync(chainFeature, result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                // FIXME: Get defaultOperation
                return editConfig(editConfigStructure, null);
            }, Executors.newSingleThreadExecutor());

        chainFeature = Futures.transformAsync(chainFeature, result -> {
            if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
            }
            return commit();
        }, Executors.newSingleThreadExecutor());

        chainFeature = Futures.transformAsync(chainFeature, result -> {
            if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
            }
            return unlock();
        }, Executors.newSingleThreadExecutor());

        Futures.addCallback(chainFeature, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(DOMRpcResult result) {
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
    protected void createData(ServerRequest<? super CreateResourceResult> request, Data path, Insert insert,
        NormalizedNode data) {
        try {
            ServerDataOperationsUtil.checkListAndOrderedType(path);
        } catch (RequestException cause) {
            request.completeWith(cause);
            return;
        }

    }

    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {

    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        final var fields = params.fields();
        final List<YangInstanceIdentifier> fieldPaths;
        if (fields != null) {
            final List<YangInstanceIdentifier> tmp;
            try {
                tmp = fieldsParamToPaths(path.inference().modelContext(), path.schema(), fields);
            } catch (RequestException e) {
                request.completeWith(e);
                return;
            }
            fieldPaths = tmp.isEmpty() ? null : tmp;
        } else {
            fieldPaths = null;
        }

        final NormalizedNode node;
        try {
            if (fieldPaths != null) {
                node = readData(params.content(), path.instance(), params.withDefaults(), fieldPaths);
            } else {
                node = readData(params.content(), path.instance(), params.withDefaults());
            }
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final Data path, final NormalizedNode data) {

    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchContext patch) {

    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final NormalizedNode data) {

    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final Data path, final Insert insert,
        final NormalizedNode data) {

    }


    private ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path));
    }

    private ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path), fields);
    }

    private ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path));
    }

    private ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path), fields);
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
}
