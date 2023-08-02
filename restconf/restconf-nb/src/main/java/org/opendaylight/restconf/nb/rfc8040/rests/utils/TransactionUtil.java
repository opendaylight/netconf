/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for common methods of transactions.
 */
public final class TransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionUtil.class);

    private TransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Merged parents of data.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param transaction   A handle to a set of DS operations
     */
    // FIXME: this method should only be invoked in MdsalRestconfStrategy, and even then only if we are crossing
    //        an implicit list.
    public static void ensureParentsByMerge(final YangInstanceIdentifier path,
                                            final EffectiveModelContext schemaContext,
                                            final RestconfTransaction transaction) {
        final var normalizedPathWithoutChildArgs = new ArrayList<PathArgument>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final var it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final var pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.of(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        transaction.merge(rootNormalizedPath,
            ImmutableNodes.fromInstanceId(schemaContext, YangInstanceIdentifier.of(normalizedPathWithoutChildArgs)));
    }

    /**
     * Synchronize access to a path resource, translating any failure to a {@link RestconfDocumentedException}.
     *
     * @param <T> The type being accessed
     * @param future Access future
     * @param path Path being accessed
     * @return The accessed value
     * @throws RestconfDocumentedException if commit fails
     */
    public static <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RestconfDocumentedException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestconfDocumentedException("Interrupted while accessing " + path, e);
        }
    }

    /**
     * Synchronize commit future, translating any failure to a {@link RestconfDocumentedException}.
     *
     * @param future Commit future
     * @param txType Transaction type name
     * @param path Modified path
     * @throws RestconfDocumentedException if commit fails
     */
    public static void syncCommit(final ListenableFuture<? extends CommitInfo> future, final String txType,
            final YangInstanceIdentifier path) {
        try {
            future.get();
        } catch (InterruptedException e) {
            LOG.warn("Transaction({}) FAILED!", txType, e);
            throw new RestconfDocumentedException("Transaction failed", e);
        } catch (ExecutionException e) {
            LOG.warn("Transaction({}) FAILED!", txType, e);
            throw decodeException(e, txType, path);
        }
        LOG.trace("Transaction({}) SUCCESSFUL", txType);
    }

    public static @NonNull RestconfDocumentedException decodeException(final Throwable throwable,
            final String txType, final YangInstanceIdentifier path) {
        return decodeException(throwable, throwable, txType, path);
    }

    private static @NonNull RestconfDocumentedException decodeException(final ExecutionException ex,
            final String txType, final YangInstanceIdentifier path) {
        return decodeException(ex, ex.getCause(), txType, path);
    }

    private static @NonNull RestconfDocumentedException decodeException(final Throwable ex, final Throwable cause,
            final String txType, final YangInstanceIdentifier path) {
        if (cause instanceof TransactionCommitFailedException) {
            // If device send some error message we want this message to get to client and not just to throw it away
            // or override it with new generic message. We search for NetconfDocumentedException that was send from
            // netconfSB and we create RestconfDocumentedException accordingly.
            for (var error : Throwables.getCausalChain(cause)) {
                if (error instanceof DocumentedException documentedError) {
                    final ErrorTag errorTag = documentedError.getErrorTag();
                    if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
                        return new RestconfDocumentedException(ex, new RestconfError(ErrorType.PROTOCOL,
                            ErrorTag.DATA_EXISTS, "Data already exists", path));
                    } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
                        return new RestconfDocumentedException(ex, new RestconfError(ErrorType.PROTOCOL,
                            ErrorTag.DATA_MISSING, "Data does not exist", path));
                    }
                } else if (error instanceof NetconfDocumentedException netconfError) {
                    return new RestconfDocumentedException(netconfError.getMessage(), netconfError.getErrorType(),
                        netconfError.getErrorTag(), ex);
                }
            }

            return new RestconfDocumentedException("Transaction(" + txType + ") not committed correctly", ex);
        }

        return new RestconfDocumentedException("Transaction(" + txType + ") failed", ex);
    }
}
