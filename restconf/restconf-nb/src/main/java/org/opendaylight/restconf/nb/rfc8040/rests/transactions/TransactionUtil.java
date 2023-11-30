/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for common methods of transactions.
 */
final class TransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionUtil.class);

    private TransactionUtil() {
        // Hidden on purpose
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
    static <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RestconfDocumentedException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestconfDocumentedException("Interrupted while accessing " + path, e);
        }
    }

    static @NonNull RestconfDocumentedException decodeException(final Throwable ex, final String txType,
            final YangInstanceIdentifier path, final EffectiveModelContext context) {
        if (ex instanceof TransactionCommitFailedException) {
            // If device send some error message we want this message to get to client and not just to throw it away
            // or override it with new generic message. We search for NetconfDocumentedException that was send from
            // netconfSB and we create RestconfDocumentedException accordingly.
            for (var error : Throwables.getCausalChain(ex)) {
                if (error instanceof DocumentedException documentedError) {
                    final ErrorTag errorTag = documentedError.getErrorTag();
                    if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
                        return new RestconfDocumentedException(ex, new RestconfError(ErrorType.PROTOCOL,
                            ErrorTag.DATA_EXISTS, "Data already exists", path), context);
                    } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
                        return new RestconfDocumentedException(ex, new RestconfError(ErrorType.PROTOCOL,
                            ErrorTag.DATA_MISSING, "Data does not exist", path), context);
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

    static boolean isListPath(final YangInstanceIdentifier path, final EffectiveModelContext modelContext) {
        try {
            final var pathQNames = path.getPathArguments().stream()
                .map(YangInstanceIdentifier.PathArgument::getNodeType).toList();
            final var schemaNode = modelContext.findDataTreeChild(pathQNames).orElse(null);
            return schemaNode instanceof ListSchemaNode || schemaNode instanceof LeafListSchemaNode;
        } catch (NoSuchElementException | IllegalArgumentException e) {
            // FIXME path validation before execution is expected to be performed on upper level
            LOG.debug("Invalid path: {}", path, e);
            return false;
        }
    }
}
