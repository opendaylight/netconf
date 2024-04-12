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
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.api.ErrorMessage;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerErrorPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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
     * Synchronize access to a path resource, translating any failure to a {@link ServerException}.
     *
     * @param <T> The type being accessed
     * @param future Access future
     * @param path Path being accessed
     * @return The accessed value
     * @throws ServerException if commit fails
     */
    static <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path)
            throws ServerException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new ServerException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException("Interrupted while accessing " + path, e);
        }
    }

    static @NonNull ServerException decodeException(final Throwable ex, final String txType,
            final YangInstanceIdentifier path, final DatabindContext databind) {
        if (ex instanceof TransactionCommitFailedException) {
            // If device send some error message we want this message to get to client and not just to throw it away
            // or override it with new generic message. We search for NetconfDocumentedException that was send from
            // netconfSB and we create RestconfDocumentedException accordingly.
            for (var error : Throwables.getCausalChain(ex)) {
                if (error instanceof DocumentedException documentedError) {
                    final ErrorTag errorTag = documentedError.getErrorTag();
                    if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
                        return new ServerException(new ServerError(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                            new ErrorMessage("Data already exists"), null, new ServerErrorPath(databind, path), null),
                            ex);
                    } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
                        return new ServerException(new ServerError(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                            new ErrorMessage("Data does not exist"), null, new ServerErrorPath(databind, path), null),
                            ex);
                    }
                } else if (error instanceof NetconfDocumentedException netconfError) {
                    //
                    final var errorMessage = netconfError.getMessage();

                    return new ServerException(new ServerError(netconfError.getErrorType(), netconfError.getErrorTag(),
                        errorMessage != null ? new ErrorMessage(errorMessage) : null, null, null, null), ex);
                }
            }

            return new ServerException("Transaction(" + txType + ") not committed correctly", ex);
        }

        return new ServerException("Transaction(" + txType + ") failed", ex);
    }
}
