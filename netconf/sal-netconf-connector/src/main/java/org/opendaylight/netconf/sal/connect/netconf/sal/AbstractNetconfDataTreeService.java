/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfDataTreeService implements NetconfDataTreeService {
    private static final class Candidate extends AbstractNetconfDataTreeService {
        Candidate(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport) {
            super(id, netconfOps, rollbackSupport);
        }

        /**
         * This has to be non blocking since it is called from a callback on commit and it is netty threadpool that is
         * really sensitive to blocking calls.
         */
        @Override
        public ListenableFuture<? extends DOMRpcResult> discardChanges() {
            return netconfOps.discardChanges(new NetconfRpcFutureCallback("Discard candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockSingle() {
            return netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock candidate", id));
        }

        @Override
        List<ListenableFuture<? extends DOMRpcResult>> unlockImpl() {
            return List.of(netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id)));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
            return defaultOperation == null ? netconfOps.editConfigCandidate(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigCandidate(callback, editStructure, defaultOperation, rollbackSupport);
        }
    }

    private static final class Running extends AbstractNetconfDataTreeService {
        Running(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport) {
            super(id, netconfOps, rollbackSupport);
        }

        @Override
        public ListenableFuture<DOMRpcResult> discardChanges() {
            // Changes cannot be discarded from running
            return RPC_SUCCESS;
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockSingle() {
            return netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id));
        }

        @Override
        List<ListenableFuture<? extends DOMRpcResult>> unlockImpl() {
            return List.of(netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id)));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit running", id);
            return defaultOperation == null ? netconfOps.editConfigRunning(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigRunning(callback, editStructure, defaultOperation, rollbackSupport);
        }
    }

    private static final class CandidateWithRunning extends AbstractNetconfDataTreeService {
        private final Candidate candidate;
        private final Running running;

        CandidateWithRunning(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
                final boolean rollbackSupport) {
            super(id, netconfOps, rollbackSupport);
            candidate = new Candidate(id, netconfOps, rollbackSupport);
            running = new Running(id, netconfOps, rollbackSupport);
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> discardChanges() {
            return candidate.discardChanges();
        }

        @Override
        ListenableFuture<DOMRpcResult> lockSingle() {
            throw new UnsupportedOperationException();
        }

        @Override
        List<ListenableFuture<? extends DOMRpcResult>> lockImpl() {
            return List.of(candidate.lockSingle(), running.lockSingle());
        }

        @Override
        List<ListenableFuture<? extends DOMRpcResult>> unlockImpl() {
            return List.of(running.unlock(), candidate.unlock());
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            return candidate.editConfig(editStructure, defaultOperation);
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfDataTreeService.class);
    private static final ListenableFuture<DOMRpcResult> RPC_SUCCESS =
        Futures.immediateFuture(new DefaultDOMRpcResult());

    final @NonNull RemoteDeviceId id;
    final NetconfBaseOps netconfOps;
    final boolean rollbackSupport;

    // FIXME: what do we do with locks acquired before this got flipped?
    private volatile boolean isLockAllowed = true;

    // FIXME: track futures instead
    private volatile boolean locked = false;

    AbstractNetconfDataTreeService(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
            final boolean rollbackSupport) {
        this.id = requireNonNull(id);
        this.netconfOps = requireNonNull(netconfOps);
        this.rollbackSupport = rollbackSupport;
    }

    public static @NonNull AbstractNetconfDataTreeService of(final RemoteDeviceId id,
            final MountPointContext mountContext, final DOMRpcService rpc,
            final NetconfSessionPreferences netconfSessionPreferences) {
        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mountContext);
        final boolean rollbackSupport = netconfSessionPreferences.isRollbackSupported();

        // Examine preferences and decide which implementation to use
        if (netconfSessionPreferences.isCandidateSupported()) {
            return netconfSessionPreferences.isRunningWritable()
                ? new CandidateWithRunning(id, netconfOps, rollbackSupport)
                    : new Candidate(id, netconfOps, rollbackSupport);
        } else if (netconfSessionPreferences.isRunningWritable()) {
            return new Running(id, netconfOps, rollbackSupport);
        } else {
            throw new IllegalArgumentException("Device " + id.getName() + " has advertised neither :writable-running "
                + "nor :candidate capability. Failed to establish session, as at least one of these must be "
                + "advertised.");
        }
    }

    @Override
    public synchronized ListenableFuture<DOMRpcResult> lock() {
        if (!isLockAllowed) {
            LOG.trace("Lock is not allowed by device configuration, ignoring lock results: {}", id);
            return RPC_SUCCESS;
        }

        final ListenableFuture<DOMRpcResult> result = mergeFutures(lockImpl());
        Futures.addCallback(result, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final var errors = result.getErrors();
                if (errors.isEmpty()) {
                    LOG.debug("{}: Lock successful.", id);
                    locked = true;
                    return;
                }
                if (allWarnings(errors)) {
                    LOG.info("{}: Lock successful with warnings {}", errors, id);
                    locked = true;
                    return;
                }

                LOG.warn("{}: Lock failed with errors {}", id, errors);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("{}: Lock failed.", id, throwable);
            }
        }, MoreExecutors.directExecutor());

        return result;
    }

    List<ListenableFuture<? extends DOMRpcResult>> lockImpl() {
        return List.of(lockSingle());
    }

    abstract ListenableFuture<? extends DOMRpcResult> lockSingle();

    @Override
    public synchronized ListenableFuture<DOMRpcResult> unlock() {
        // FIXME: deal with lock with lifecycle?
        if (!isLockAllowed) {
            LOG.trace("Unlock is not allowed: {}", id);
            return RPC_SUCCESS;
        }

        final ListenableFuture<DOMRpcResult> result = mergeFutures(unlockImpl());
        Futures.addCallback(result, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final var errors = result.getErrors();
                if (errors.isEmpty()) {
                    LOG.debug("{}: Unlock successful.", id);
                    locked = false;
                    return;
                }

                if (errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING)) {
                    LOG.info("{}: Unlock successful with warnings {}", errors, id);
                    locked = false;
                    return;
                }

                LOG.error("{}: Unlock failed with errors {}", id, errors);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("{}: Unlock failed.", id, throwable);
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

    abstract List<ListenableFuture<? extends DOMRpcResult>> unlockImpl();

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(final YangInstanceIdentifier path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path));
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path), fields);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final YangInstanceIdentifier path) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path));
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path), fields);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        return editConfig(
            netconfOps.createEditConfigStrcture(Optional.ofNullable(data), Optional.of(ModifyAction.MERGE), path),
            defaultOperation.orElse(null));
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        return editConfig(
            netconfOps.createEditConfigStrcture(Optional.ofNullable(data), Optional.of(ModifyAction.REPLACE), path),
            defaultOperation.orElse(null));
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        return editConfig(
            netconfOps.createEditConfigStrcture(Optional.ofNullable(data), Optional.of(ModifyAction.CREATE), path),
            defaultOperation.orElse(null));
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return editConfig(netconfOps.createEditConfigStrcture(Optional.empty(), Optional.of(ModifyAction.DELETE), path),
            null);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return editConfig(netconfOps.createEditConfigStrcture(Optional.empty(), Optional.of(ModifyAction.REMOVE), path),
            null);
    }

    @Override
    public synchronized ListenableFuture<? extends CommitInfo> commit(
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final SettableFuture<CommitInfo> commitFuture = SettableFuture.create();

        // First complete all resultsFutures and merge them ...
        final ListenableFuture<DOMRpcResult> resultErrors = mergeFutures(resultsFutures);

        // ... then evaluate if there are any problems
        Futures.addCallback(resultErrors, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final Collection<? extends RpcError> errors = result.getErrors();
                if (!allWarnings(errors)) {
                    Futures.whenAllComplete(discardAndUnlock()).run(
                        () -> commitFuture.setException(mapRpcErrorsToNetconfDocException(id, errors)),
                        MoreExecutors.directExecutor());
                    return;
                }

                // ... no problems so far, initiate commit
                netconfOps.commit(new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(final DOMRpcResult result) {
                        final Collection<? extends RpcError> errors = result.getErrors();
                        if (errors.isEmpty()) {
                            commitFuture.set(CommitInfo.empty());
                            return;
                        }
                        if (allWarnings(errors)) {
                            LOG.info("{}: Commit successful with warnings {}", errors, id);
                            commitFuture.set(CommitInfo.empty());
                            return;
                        }
                        Futures.whenAllComplete(discardAndUnlock()).run(
                            () -> commitFuture.setException(mapRpcErrorsToNetconfDocException(id, errors)),
                            MoreExecutors.directExecutor());
                    }

                    @Override
                    public void onFailure(final Throwable throwable) {
                        Futures.whenAllComplete(discardAndUnlock()).run(
                            () -> commitFuture.setException(throwable),
                            MoreExecutors.directExecutor());
                    }
                });
            }

            @Override
            public void onFailure(final Throwable throwable) {
                Futures.whenAllComplete(discardAndUnlock()).run(
                    () -> commitFuture.setException(throwable),
                    MoreExecutors.directExecutor());
            }
        }, MoreExecutors.directExecutor());

        return commitFuture;
    }

    @Override
    public final Object getDeviceId() {
        return id;
    }

    final void setLockAllowed(final boolean isLockAllowedOrig) {
        this.isLockAllowed = isLockAllowedOrig;
    }

    abstract ListenableFuture<? extends DOMRpcResult> editConfig(DataContainerChild<?, ?> editStructure,
        @Nullable ModifyAction defaultOperation);

    private static void checkEditable(final LogicalDatastoreType store) {
        checkArgument(store == LogicalDatastoreType.CONFIGURATION, "Can only edit configuration data, not %s", store);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private List<ListenableFuture<?>> discardAndUnlock() {
        return List.of(discardChanges(), unlock());
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static void extractResult(final List<DOMRpcResult> domRpcResults,
            final SettableFuture<RpcResult<Void>> transformed, final RemoteDeviceId deviceId) {
        final List<RpcError> errorsList = domRpcResults.stream().flatMap(result -> result.getErrors().stream())
                .collect(Collectors.toList());

        if (errorsList.isEmpty()) {
            transformed.set(RpcResultBuilder.<Void>success().build());
        } else {
            transformed.setException(mapRpcErrorsToNetconfDocException(deviceId, errorsList));
        }
    }

    private static NetconfDocumentedException mapRpcErrorsToNetconfDocException(final RemoteDeviceId id,
            final Collection<? extends RpcError> errors) {
        DocumentedException.ErrorType errType = DocumentedException.ErrorType.APPLICATION;
        DocumentedException.ErrorSeverity errSeverity = DocumentedException.ErrorSeverity.ERROR;
        StringJoiner msgBuilder = new StringJoiner(" ");
        String errorTag = "operation-failed";
        for (final RpcError error : errors) {
            final RpcError.ErrorType errorType = error.getErrorType();
            switch (errorType) {
                case RPC:
                    errType = DocumentedException.ErrorType.RPC;
                    break;
                case PROTOCOL:
                    errType = DocumentedException.ErrorType.PROTOCOL;
                    break;
                case TRANSPORT:
                    errType = DocumentedException.ErrorType.TRANSPORT;
                    break;
                case APPLICATION:
                default:
                    errType = DocumentedException.ErrorType.APPLICATION;
                    break;
            }
            final RpcError.ErrorSeverity severity = error.getSeverity();
            switch (severity) {
                case WARNING:
                    errSeverity = DocumentedException.ErrorSeverity.WARNING;
                    break;
                case ERROR:
                default:
                    errSeverity = DocumentedException.ErrorSeverity.ERROR;
                    break;
            }
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }
        return new NetconfDocumentedException(id + ":RPC during tx failed. " + msgBuilder.toString(), errType,
                DocumentedException.ErrorTag.from(errorTag), errSeverity);
    }

    // There is not much sense in exposing the list of futures related to the lock operation,
    // the consumer won't be able to do anything useful with them anyway
    private static ListenableFuture<DOMRpcResult> mergeFutures(
            final List<ListenableFuture<? extends DOMRpcResult>> futures) {
        return Futures.whenAllComplete(futures).call(() -> {
            if (futures.size() == 1) {
                // Fast path
                return Futures.getDone(futures.get(0));
            }

            final var builder = ImmutableList.<RpcError>builder();
            for (ListenableFuture<? extends DOMRpcResult> future : futures) {
                builder.addAll(Futures.getDone(future).getErrors());
            }
            return new DefaultDOMRpcResult(null, builder.build());
        }, MoreExecutors.directExecutor());
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }
}
