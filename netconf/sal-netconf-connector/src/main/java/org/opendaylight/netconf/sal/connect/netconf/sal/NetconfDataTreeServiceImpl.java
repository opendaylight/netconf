/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
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
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDataTreeServiceImpl implements NetconfDataTreeService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataTreeServiceImpl.class);

    private final RemoteDeviceId id;
    private final NetconfBaseOps netconfOps;
    private final boolean rollbackSupport;
    private final boolean candidateSupported;
    private final boolean runningWritable;

    private boolean isLockAllowed = true;

    public NetconfDataTreeServiceImpl(final RemoteDeviceId id, final MountPointContext mountContext,
                                      final DOMRpcService rpc,
                                      final NetconfSessionPreferences netconfSessionPreferences) {
        this.id = id;
        this.netconfOps = new NetconfBaseOps(rpc, mountContext);
        // get specific attributes from netconf preferences and get rid of it
        // no need to keep the entire preferences object, its quite big with all the capability QNames
        candidateSupported = netconfSessionPreferences.isCandidateSupported();
        runningWritable = netconfSessionPreferences.isRunningWritable();
        rollbackSupport = netconfSessionPreferences.isRollbackSupported();
        Preconditions.checkArgument(candidateSupported || runningWritable,
                "Device %s has advertised neither :writable-running nor :candidate capability."
                        + "At least one of these should be advertised. Failed to establish a session.", id.getName());
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> lock() {
        final SettableFuture<DOMRpcResult> lockRunning = SettableFuture.create();
        final SettableFuture<DOMRpcResult> lock = SettableFuture.create();

        if (candidateSupported) {
            final SettableFuture<DOMRpcResult> lockCandidate = SettableFuture.create();
            if (runningWritable) {
                Futures.addCallback(lockRunning, new FutureCallback<>() {
                    @Override
                    public void onSuccess(final DOMRpcResult result) {
                        if (isSuccess(result)) {
                            lockCandidate(lockCandidate);
                        } else {
                            lockCandidate.set(result);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        lockCandidate.setException(throwable);
                    }
                }, MoreExecutors.directExecutor());

                lockRunning(lockRunning);
                lock.setFuture(lockCandidate);
                Futures.addCallback(lockCandidate, new FutureCallback<>() {
                    @Override
                    public void onSuccess(final DOMRpcResult result) {
                        if (!isSuccess(result)) {
                            unlockRunning();
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        unlockRunning();
                    }
                }, MoreExecutors.directExecutor());
            }
            lockCandidate(lockCandidate);
            lock.setFuture(lockCandidate);
        } else {
            lockRunning(lockRunning);
            lock.setFuture(lockRunning);
        }
        return lock;
    }

    @Override
    public synchronized void unlock() {
        if (candidateSupported) {
            unlockCandidate();
            if (runningWritable) {
                unlockRunning();
            }
        } else {
            unlockRunning();
        }
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    @Override
    public void discardChanges() {
        if (candidateSupported) {
            netconfOps.discardChanges(new NetconfRpcFutureCallback("Discarding candidate", id));
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(YangInstanceIdentifier path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path));
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final YangInstanceIdentifier path) {
        return netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path));
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
                                                                       final YangInstanceIdentifier path,
                                                                       final NormalizedNode<?, ?> data,
                                                                       final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.MERGE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> replace(
            final LogicalDatastoreType store,
            final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.REPLACE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
                                                                        final YangInstanceIdentifier path,
                                                                        final NormalizedNode<?, ?> data,
                                                                        final Optional<ModifyAction> defaultOperation) {
        checkEditable(store);
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.ofNullable(data),
                Optional.of(ModifyAction.CREATE), path);

        return editConfig(defaultOperation, editStructure);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
                                                                        final YangInstanceIdentifier path) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.empty(),
                Optional.of(ModifyAction.DELETE), path);

        return editConfig(Optional.empty(), editStructure);
    }

    @Override
    public synchronized ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
                                                                        final YangInstanceIdentifier path) {
        final DataContainerChild<?, ?> editStructure = netconfOps.createEditConfigStrcture(Optional.empty(),
                Optional.of(ModifyAction.REMOVE), path);

        return editConfig(Optional.empty(), editStructure);
    }

    @Override
    public ListenableFuture<? extends CommitInfo> commit(
            List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final SettableFuture<CommitInfo> resultFuture = SettableFuture.create();
        Futures.addCallback(performCommit(resultsFutures), new FutureCallback<>() {
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

    @Override
    public Object getDeviceId() {
        return id;
    }

    void setLockAllowed(final boolean isLockAllowedOrig) {
        this.isLockAllowed = isLockAllowedOrig;
    }

    private ListenableFuture<? extends DOMRpcResult> editConfig(final Optional<ModifyAction> defaultOperation,
                                                                final DataContainerChild<?, ?> editStructure) {
        if (candidateSupported) {
            return editConfigCandidate(defaultOperation, editStructure);
        } else {
            return editConfigRunning(defaultOperation, editStructure);
        }
    }

    private ListenableFuture<? extends DOMRpcResult> editConfigRunning(final Optional<ModifyAction> defaultOperation,
                                                                       final DataContainerChild<?, ?> editStructure) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit running", id);
        if (defaultOperation.isPresent()) {
            return netconfOps.editConfigRunning(callback, editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            return netconfOps.editConfigRunning(callback, editStructure, rollbackSupport);
        }
    }

    private ListenableFuture<? extends DOMRpcResult> editConfigCandidate(final Optional<ModifyAction> defaultOperation,
                                                                         final DataContainerChild<?, ?> editStructure) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
        if (defaultOperation.isPresent()) {
            return netconfOps.editConfigCandidate(callback, editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            return netconfOps.editConfigCandidate(callback, editStructure, rollbackSupport);
        }
    }

    private void lockRunning(SettableFuture<DOMRpcResult> lockFeature) {
        if (isLockAllowed) {
            lockFeature.setFuture(netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id)));
        } else {
            LOG.trace("Lock is not allowed: {}", id);
            lockFeature.setFuture(Futures.immediateFuture(new DefaultDOMRpcResult()));
        }
    }

    private void unlockRunning() {
        if (isLockAllowed) {
            netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        } else {
            LOG.trace("Unlock is not allowed: {}", id);
        }
    }

    private void lockCandidate(SettableFuture<DOMRpcResult> lockFeature) {
        if (isLockAllowed) {
            lockFeature.setFuture(netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock running", id)));
        } else {
            LOG.trace("Lock is not allowed: {}", id);
            lockFeature.setFuture(Futures.immediateFuture(new DefaultDOMRpcResult()));
        }
    }

    private void unlockCandidate() {
        if (isLockAllowed) {
            netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
        } else {
            LOG.trace("Unlock is not allowed: {}", id);
        }
    }

    private void checkEditable(final LogicalDatastoreType store) {
        checkArgument(store == LogicalDatastoreType.CONFIGURATION,
                "Can edit only configuration data, not %s", store);
    }

    private synchronized ListenableFuture<RpcResult<Void>> performCommit(
        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final SettableFuture<RpcResult<Void>> commitResult = SettableFuture.create();
        final ListenableFuture<RpcResult<Void>> result = resultsToStatus(id, resultsFutures);
        Futures.addCallback(result, new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    if (candidateSupported) {
                        final ListenableFuture<? extends DOMRpcResult> commit =
                            netconfOps.commit(new NetconfRpcFutureCallback("Commit", id));
                        commitResult.setFuture(resultsToStatus(id, Collections.singletonList(commit)));
                    } else {
                        commitResult.set(RpcResultBuilder.<Void>success().build());
                    }
                } else {
                    commitResult.set(result);
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                commitResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        return commitResult;
    }

    private static ListenableFuture<RpcResult<Void>> resultsToStatus(
            final RemoteDeviceId id, List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
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

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static boolean isSuccess(final DOMRpcResult result) {
        return result.getErrors().isEmpty();
    }
}
