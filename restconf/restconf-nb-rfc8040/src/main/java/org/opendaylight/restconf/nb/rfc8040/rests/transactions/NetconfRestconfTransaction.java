/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfRestconfTransaction extends RestconfTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfRestconfTransaction.class);

    private final NetconfDataTreeService netconfService;
    private final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures =
        Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isLocked = false;

    NetconfRestconfTransaction(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
        final ListenableFuture<? extends DOMRpcResult> lockResult = netconfService.lock();
        Futures.addCallback(lockResult, lockOperationCallback, MoreExecutors.directExecutor());
        resultsFutures.add(lockResult);
    }

    @Override
    public void cancel() {
        resultsFutures.clear();
        executeWithLogging(netconfService::discardChanges);
        executeWithLogging(netconfService::unlock);
    }

    @Override
    public void delete(final YangInstanceIdentifier path) {
        enqueueOperation(() -> netconfService.delete(CONFIGURATION, path));
    }

    @Override
    public void remove(final YangInstanceIdentifier path) {
        enqueueOperation(() -> netconfService.remove(CONFIGURATION, path));
    }

    @Override
    public void merge(final YangInstanceIdentifier path, final NormalizedNode data) {
        enqueueOperation(() -> netconfService.merge(CONFIGURATION, path, data, Optional.empty()));
    }

    @Override
    public void create(final YangInstanceIdentifier path, final NormalizedNode data,
            final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);

            for (final NormalizedNode child : ((NormalizedNodeContainer<?>) data).body()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                enqueueOperation(() -> netconfService.create(CONFIGURATION, childPath, child, Optional.empty()));
            }
        } else {
            enqueueOperation(() -> netconfService.create(CONFIGURATION, path, data, Optional.empty()));
        }
    }

    @Override
    public void replace(final YangInstanceIdentifier path, final NormalizedNode data,
            final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);

            for (final NormalizedNode child : ((NormalizedNodeContainer<?>) data).body()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                enqueueOperation(() -> netconfService.replace(CONFIGURATION, childPath, child, Optional.empty()));
            }
        } else {
            enqueueOperation(() -> netconfService.replace(CONFIGURATION, path, data, Optional.empty()));
        }
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        final SettableFuture<CommitInfo> commitResult = SettableFuture.create();

        // First complete all resultsFutures and merge them ...
        final ListenableFuture<DOMRpcResult> resultErrors = mergeFutures(resultsFutures);

        // ... then evaluate if there are any problems
        Futures.addCallback(resultErrors, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                final Collection<? extends RpcError> errors = result.getErrors();
                if (!allWarnings(errors)) {
                    Futures.whenAllComplete(discardAndUnlock()).run(
                        () -> commitResult.setException(toCommitFailedException(errors)),
                        MoreExecutors.directExecutor());
                    return;
                }

                // ... no problems so far, initiate commit
                Futures.addCallback(netconfService.commit(), new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(final DOMRpcResult rpcResult) {
                        final Collection<? extends RpcError> errors = result.getErrors();
                        if (errors.isEmpty()) {
                            Futures.whenAllComplete(netconfService.unlock()).run(
                                () -> commitResult.set(CommitInfo.empty()),
                                MoreExecutors.directExecutor());
                        } else if (allWarnings(errors)) {
                            LOG.info("Commit successful with warnings {}", errors);
                            Futures.whenAllComplete(netconfService.unlock()).run(
                                () -> commitResult.set(CommitInfo.empty()),
                                MoreExecutors.directExecutor());
                        } else {
                            Futures.whenAllComplete(discardAndUnlock()).run(
                                () -> commitResult.setException(toCommitFailedException(errors)),
                                MoreExecutors.directExecutor());
                        }
                    }

                    @Override
                    public void onFailure(final Throwable throwable) {
                        Futures.whenAllComplete(discardAndUnlock()).run(
                            () -> commitResult.setException(throwable),
                            MoreExecutors.directExecutor());
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                Futures.whenAllComplete(discardAndUnlock()).run(() -> commitResult.setException(throwable),
                    MoreExecutors.directExecutor());
            }
        }, MoreExecutors.directExecutor());

        return FluentFuture.from(commitResult);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private List<ListenableFuture<?>> discardAndUnlock() {
        // execute discard & unlock operations only if lock operation was completed successfully
        if (isLocked) {
            return List.of(netconfService.discardChanges(), netconfService.unlock());
        } else {
            return Collections.emptyList();
        }
    }

    private final FutureCallback<DOMRpcResult> lockOperationCallback = new FutureCallback<>() {
        @Override
        public void onSuccess(final DOMRpcResult rpcResult) {
            if (rpcResult != null && allWarnings(rpcResult.getErrors())) {
                isLocked = true;
            }
        }

        @Override
        public void onFailure(final Throwable throwable) {
            // do nothing
        }
    };

    private void enqueueOperation(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        final ListenableFuture<? extends DOMRpcResult> operationFuture;
        synchronized (resultsFutures) {
            // if we only have result for the lock operation ...
            if (resultsFutures.size() == 1) {
                operationFuture = Futures.transformAsync(resultsFutures.get(0),
                    result -> {
                        // ... then add new operation to the chain if lock was successful
                        if (result != null && (result.getErrors().isEmpty() || allWarnings(result.getErrors()))) {
                            return operation.get();
                        } else {
                            return Futures.immediateFailedFuture(new NetconfDocumentedException("Lock operation failed",
                                DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.LOCK_DENIED,
                                ErrorSeverity.ERROR));
                        }
                    },
                    MoreExecutors.directExecutor());
            } else {
                // ... otherwise just add operation to the execution chain
                operationFuture = Futures.transformAsync(resultsFutures.get(resultsFutures.size() - 1),
                    future -> operation.get(),
                    MoreExecutors.directExecutor());
            }
            // ... finally save operation related future to the list
            resultsFutures.add(operationFuture);
        }
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
                builder.addAll(Futures.getDone(future).getErrors());
            }
            return new DefaultDOMRpcResult(null, builder.build());
        }, MoreExecutors.directExecutor());
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static TransactionCommitFailedException toCommitFailedException(
            final Collection<? extends RpcError> errors) {
        DocumentedException.ErrorType errType = DocumentedException.ErrorType.APPLICATION;
        ErrorSeverity errSeverity = ErrorSeverity.ERROR;
        StringJoiner msgBuilder = new StringJoiner(" ");
        String errorTag = "operation-failed";
        for (final RpcError error : errors) {
            switch (error.getErrorType()) {
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
            errSeverity = error.getSeverity().toNetconf();
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }

        return new TransactionCommitFailedException("Netconf transaction commit failed",
            new NetconfDocumentedException("RPC during tx failed. " + msgBuilder.toString(), errType,
                DocumentedException.ErrorTag.from(errorTag), errSeverity));
    }

    private static void executeWithLogging(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        final ListenableFuture<? extends DOMRpcResult> operationResult = operation.get();
        Futures.addCallback(operationResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult rpcResult) {
                if (rpcResult != null && !rpcResult.getErrors().isEmpty()) {
                    LOG.error("Errors occurred during processing of the RPC operation: {}",
                        rpcResult.getErrors().stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Error processing operation", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == RpcError.ErrorSeverity.WARNING);
    }
}
