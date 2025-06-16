/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides common methods for manipulating and retrieving configuration and operational data on a NETCONF
 * device with a candidate or running data store.
 */
public abstract class AbstractDataStore implements DataStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataStore.class);

    static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    final NetconfBaseOps netconfOps;
    final RemoteDeviceId id;
    final boolean rollbackSupport;
    final boolean lockDatastore;

    AbstractDataStore(final NetconfBaseOps netconfOps, final RemoteDeviceId id, boolean rollbackSupport,
            boolean lockDatastore) {
        this.netconfOps = requireNonNull(netconfOps);
        this.id = requireNonNull(id);
        this.rollbackSupport = rollbackSupport;
        this.lockDatastore = lockDatastore;
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final DatabindContext databind,
            final RemoteDeviceServices.Rpcs rpcs, final NetconfSessionPreferences sessionPreferences,
            final boolean lockDatastore) {
        final var netconfOps = new NetconfBaseOps(databind, rpcs);
        return of(id, netconfOps, sessionPreferences, lockDatastore);
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
            final NetconfSessionPreferences sessionPreferences, final boolean lockDatastore) {
        final boolean rollbackSupport = sessionPreferences.isRollbackSupported();

        // Examine preferences and decide which implementation to use
        if (sessionPreferences.isCandidateSupported()) {
            if (sessionPreferences.isRunningWritable()) {
                LOG.info("Initialize CandidateWithRunning data-store operations for device {}", id);
                return new CandidateWithRunning(netconfOps, id, rollbackSupport, lockDatastore);
            }
            LOG.info("Initialize Candidate data-store operations for device {}", id);
            return new Candidate(netconfOps, id, rollbackSupport, lockDatastore);
        } else if (sessionPreferences.isRunningWritable()) {
            LOG.info("Initialize Running data-store operations for device {}", id);
            return new Running(netconfOps, id, rollbackSupport, lockDatastore);
        } else {
            LOG.error("Device has advertised neither :writable-running nor :candidate capability."
                    + " Device non-module based capabilities {}.", sessionPreferences.nonModuleCaps());
            throw new IllegalArgumentException("Device " + id.name() + " has advertised neither :writable-running nor "
                + ":candidate capability. Failed to establish session, as at least one of these must be advertised.");
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path, fields);
            case OPERATIONAL -> getData(path, fields);
        };
    }

    abstract ListenableFuture<? extends DOMRpcResult> unlock();

    abstract ListenableFuture<? extends DOMRpcResult> lock();

    ListenableFuture<? extends DOMRpcResult> addLockBeforeFuture(
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture) {
        return Futures.transformAsync(lock(), result -> {
            if (result != null && !result.errors().isEmpty() && noWarnings(result.errors())) {
                return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
            }
            return nextFuture.get();
        }, MoreExecutors.directExecutor());
    }

    /**
     * Add an Unlock operation execution after provided feature completes, ignoring the Unlock operation result.
     * The returned future completes when the Unlock operation is finalized, carrying the result from the provided
     * future.
     *
     * @param future The future after which the Unlock operation should be executed.
     * @return A chained future representing the completion of both the provided future and the Unlock operation,
     *         with the result of the provided future if no failures occur.
     */
    ListenableFuture<? extends DOMRpcResult> addUnlockAfterFuture(
            final ListenableFuture<? extends DOMRpcResult> future) {
        return Futures.transformAsync(future, result -> {
            if (result != null && !result.errors().isEmpty() && noWarnings(result.errors())) {
                return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
            }
            return Futures.transform(unlock(), ignored -> {
                if (ignored != null && !ignored.errors().isEmpty() && noWarnings(ignored.errors())) {
                    LOG.warn("Unlock operation failed with errors {}, result ignored.", ignored.errors());
                }
                return result;
            }, MoreExecutors.directExecutor());
        }, MoreExecutors.directExecutor());
    }

    /**
     * If the provided {@code future} fails, this method chains a call to {@link AbstractDataStore#cancel()} into the
     * returned future. The result of the returned future is the result of the provided future. The result from
     * {@link AbstractDataStore#cancel()} is ignored and only logged.
     *
     * @param future The {@link ListenableFuture} to be checked for failure.
     * @return A {@link ListenableFuture} that includes a chained {@link AbstractDataStore#cancel()} call in case of
     *         any failure. The result of this future is the result of the {@code future} provided as a parameter.
     */
    ListenableFuture<? extends DOMRpcResult> addCancelIfFails(final ListenableFuture<? extends DOMRpcResult> future) {
        final var handleRpcResult = Futures.transformAsync(future, result -> {
            if (result != null && !result.errors().isEmpty() && noWarnings(result.errors())) {
                return Futures.transform(cancel(), ignored -> result, MoreExecutors.directExecutor());
            }
            return Futures.immediateFuture(result);
        }, MoreExecutors.directExecutor());

        return Futures.catchingAsync(handleRpcResult, Throwable.class, t ->
            Futures.transformAsync(cancel(), ignored ->
                Futures.immediateFailedFuture(t), MoreExecutors.directExecutor()), MoreExecutors.directExecutor());
    }

    static boolean noWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return !errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    static NetconfDocumentedException getNetconfDocumentedException(final Collection<? extends RpcError> errors) {
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

    private ListenableFuture<Optional<NormalizedNode>> getData(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        if (fields.isEmpty()) {
            return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path));
        } else {
            return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path), fields);
        }
    }

    private ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        if (fields.isEmpty()) {
            return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
                Optional.of(path));
        } else {
            return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
                Optional.of(path), fields);
        }
    }
}
