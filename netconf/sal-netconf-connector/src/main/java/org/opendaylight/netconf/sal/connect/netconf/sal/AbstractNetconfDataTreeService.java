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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
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
        public void discardChanges() {
            netconfOps.discardChanges(new NetconfRpcFutureCallback("Discarding candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockSingle() {
            return netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock candidate", id) {
                @Override
                public void onFailure(final Throwable throwable) {
                    super.onFailure(throwable);
                    discardChanges();
                }
            });
        }

        @Override
        void unlockImpl() {
            netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
            return defaultOperation == null ? netconfOps.editConfigCandidate(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigCandidate(callback, editStructure, defaultOperation, rollbackSupport);
        }

        @Override
        ListenableFuture<RpcResult<Void>> commitImpl(final List<ListenableFuture<? extends DOMRpcResult>> results) {
            results.add(netconfOps.commit(new NetconfRpcFutureCallback("Commit", id)));
            final ListenableFuture<RpcResult<Void>> result = resultsToStatus(id, results);
            Futures.addCallback(result, new FutureCallback<>() {
                @Override
                public void onSuccess(final RpcResult<Void> result) {
                    unlock();
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    discardChanges();
                    unlock();
                }
            }, MoreExecutors.directExecutor());
            return result;
        }
    }

    private static final class Running extends AbstractNetconfDataTreeService {
        Running(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport) {
            super(id, netconfOps, rollbackSupport);
        }

        @Override
        public void discardChanges() {
            // Changes cannot be discarded from running
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockSingle() {
            return netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id));
        }

        @Override
        void unlockImpl() {
            netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit running", id);
            return defaultOperation == null ? netconfOps.editConfigRunning(callback, editStructure, rollbackSupport)
                : netconfOps.editConfigRunning(callback, editStructure, defaultOperation, rollbackSupport);
        }

        @Override
        ListenableFuture<RpcResult<Void>> commitImpl(final List<ListenableFuture<? extends DOMRpcResult>> results) {
            unlock();
            return resultsToStatus(id, results);
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
        public void discardChanges() {
            candidate.discardChanges();
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> lockSingle() {
            throw new UnsupportedOperationException();
        }

        @Override
        List<ListenableFuture<? extends DOMRpcResult>> lockImpl() {
            return List.of(candidate.lockSingle(), running.lockSingle());
        }

        @Override
        void unlockImpl() {
            running.unlock();
            candidate.unlock();
        }

        @Override
        ListenableFuture<? extends DOMRpcResult> editConfig(final DataContainerChild<?, ?> editStructure,
                final ModifyAction defaultOperation) {
            return candidate.editConfig(editStructure, defaultOperation);
        }

        @Override
        ListenableFuture<RpcResult<Void>> commitImpl(final List<ListenableFuture<? extends DOMRpcResult>> results) {
            return candidate.commitImpl(results);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfDataTreeService.class);

    final @NonNull RemoteDeviceId id;
    final NetconfBaseOps netconfOps;
    final boolean rollbackSupport;

    // FIXME: what do we do with locks acquired before this got flipped?
    private volatile boolean isLockAllowed = true;

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
    public synchronized List<ListenableFuture<? extends DOMRpcResult>> lock() {
        if (isLockAllowed) {
            return lockImpl();
        }
        LOG.trace("Lock is not allowed: {}", id);
        return List.of();
    }

    List<ListenableFuture<? extends DOMRpcResult>> lockImpl() {
        return List.of(lockSingle());
    }

    abstract ListenableFuture<? extends DOMRpcResult> lockSingle();

    @Override
    // FIXME: this should be asynchronous as well
    public synchronized void unlock() {
        // FIXME: deal with lock with lifecycle?
        if (isLockAllowed) {
            unlockImpl();
        } else {
            LOG.trace("Unlock is not allowed: {}", id);
        }
    }

    abstract void unlockImpl();

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
        final SettableFuture<CommitInfo> resultFuture = SettableFuture.create();
        Futures.addCallback(commitImpl(resultsFutures), new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<Void> result) {
                if (!result.isSuccessful()) {
                    final Collection<RpcError> errors = result.getErrors();
                    resultFuture.setException(new TransactionCommitFailedException(
                            String.format("Commit of transaction %s failed", this),
                            errors.toArray(new RpcError[errors.size()])));
                    return;
                }
                resultFuture.set(CommitInfo.empty());
            }

            @Override
            public void onFailure(final Throwable failure) {
                resultFuture.setException(new TransactionCommitFailedException(
                        String.format("Commit of transaction %s failed", this), failure));
            }
        }, MoreExecutors.directExecutor());
        return resultFuture;
    }

    abstract ListenableFuture<RpcResult<Void>> commitImpl(List<ListenableFuture<? extends DOMRpcResult>> results);

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
    private static ListenableFuture<RpcResult<Void>> resultsToStatus(
            final RemoteDeviceId id, final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final SettableFuture<RpcResult<Void>> transformed = SettableFuture.create();

        Futures.addCallback(Futures.allAsList(resultsFutures), new FutureCallback<>() {
            @Override
            public void onSuccess(final List<DOMRpcResult> domRpcResults) {
                if (!transformed.isDone()) {
                    extractResult(domRpcResults, transformed, id);
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                final NetconfDocumentedException exception =
                        new NetconfDocumentedException(
                                id + ":RPC during tx returned an exception" + throwable.getMessage(),
                                new Exception(throwable),
                                DocumentedException.ErrorType.APPLICATION,
                                DocumentedException.ErrorTag.OPERATION_FAILED,
                                DocumentedException.ErrorSeverity.ERROR);
                transformed.setException(exception);
            }
        }, MoreExecutors.directExecutor());

        return transformed;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static void extractResult(final List<DOMRpcResult> domRpcResults,
                                      final SettableFuture<RpcResult<Void>> transformed,
                                      final RemoteDeviceId id) {
        DocumentedException.ErrorType errType = DocumentedException.ErrorType.APPLICATION;
        DocumentedException.ErrorSeverity errSeverity = DocumentedException.ErrorSeverity.ERROR;
        StringJoiner msgBuilder = new StringJoiner(" ");
        boolean errorsEncouneterd = false;
        String errorTag = "operation-failed";

        for (final DOMRpcResult domRpcResult : domRpcResults) {
            if (!domRpcResult.getErrors().isEmpty()) {
                errorsEncouneterd = true;
                final RpcError error = domRpcResult.getErrors().iterator().next();
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
        }
        if (errorsEncouneterd) {
            final NetconfDocumentedException exception = new NetconfDocumentedException(id
                    + ":RPC during tx failed. " + msgBuilder.toString(),
                    errType,
                    DocumentedException.ErrorTag.from(errorTag),
                    errSeverity);
            transformed.setException(exception);
            return;
        }
        transformed.set(RpcResultBuilder.<Void>success().build());
    }
}
