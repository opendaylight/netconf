/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMActionException;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RestconfFuture<T> extends AbstractFuture<T> {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfFuture.class);

    private final @NonNull String txType;

    RestconfFuture(final ListenableFuture<T> future, final String txType, final YangInstanceIdentifier path) {
        this.txType = requireNonNull(txType);

        setFuture(Futures.catching(Futures.catching(future, InterruptedException.class, ex -> {
            LOG.warn("Transaction({}) FAILED!", txType, ex);
            throw new RestconfDocumentedException("Transaction failed", ex);
        }, MoreExecutors.directExecutor()), Throwable.class, cause -> {
            if (cause instanceof DOMActionException) {
                return (T) new SimpleDOMActionResult(List.of(RpcResultBuilder.newError(RpcError.ErrorType.RPC,
                    "operation-failed", cause.getMessage())));
            } else if (cause instanceof TransactionCommitFailedException) {
                /* If device send some error message we want this message to get to client
                   and not just to throw it away or override it with new generic message.
                   We search for NetconfDocumentedException that was send from netconfSB
                   and we create RestconfDocumentedException accordingly.
                 */
                final List<Throwable> causalChain = Throwables.getCausalChain(cause);
                for (Throwable error : causalChain) {
                    if (error instanceof DocumentedException) {
                        final ErrorTag errorTag = ((DocumentedException) error).getErrorTag();
                        if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                            LOG.trace("Operation via Restconf was not executed because data at {} already exists",
                                path);
                            throw new RestconfDocumentedException(cause, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.DATA_EXISTS, "Data already exists", path));
                        } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                            LOG.trace("Operation via Restconf was not executed because data at {} does not exist",
                                path);
                            throw new RestconfDocumentedException(cause, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.DATA_MISSING, "Data does not exist", path));
                        }
                    }
                    if (error instanceof NetconfDocumentedException) {
                        final NetconfDocumentedException nc = (NetconfDocumentedException) error;
                        throw new RestconfDocumentedException(error.getMessage(), nc.getErrorType(), nc.getErrorTag(),
                            cause);
                    }
                }

                throw new RestconfDocumentedException("Transaction(" + txType + ") not committed correctly", cause);
            } else {
                throw new RestconfDocumentedException("Transaction failed", cause);
            }
        }, MoreExecutors.directExecutor()));
    }

    // Guaranteed to throw RestconfDocumentedException
    T getChecked() {
        final T value;
        try {
            value = get();
        } catch (InterruptedException e) {
            LOG.warn("Transaction({}) FAILED!", txType, e);
            throw new RestconfDocumentedException("Transaction failed", e);
        } catch (ExecutionException e) {
            LOG.trace("Transaction({}) FAILED!", txType, e);
            final Throwable cause = e.getCause();
            Throwables.throwIfInstanceOf(cause, RestconfDocumentedException.class);
            // This should never be reached
            throw new RestconfDocumentedException("Transaction failed", cause);
        }
        LOG.trace("Transaction({}) SUCCESSFUL", txType);
        return value;
    }
}
