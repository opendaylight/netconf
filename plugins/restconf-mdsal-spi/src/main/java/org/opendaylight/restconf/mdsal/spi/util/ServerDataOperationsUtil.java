/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.util;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerDataOperationsUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ServerDataOperationsUtil.class);

    private ServerDataOperationsUtil() {
        // Hide on purpose
    }

    public static Data getConceptualParent(final Data data) {
        final var targetPath = data.instance();
        final var parentPath = targetPath.coerceParent();
        final var databind = data.databind();
        final var childAndStack = databind.schemaTree().enterPath(parentPath).orElseThrow();
        return new Data(databind, childAndStack.stack().toInference(), parentPath, childAndStack.node());
    }

    public static DataSchemaNode checkListAndOrderedType(final Data path) throws RequestException {
        final var dataSchemaNode = path.schema().dataSchemaNode();

        final String message;
        if (dataSchemaNode instanceof ListSchemaNode listSchema) {
            if (listSchema.isUserOrdered()) {
                return listSchema;
            }
            message = "Insert parameter can be used only with ordered-by user list.";
        } else if (dataSchemaNode instanceof LeafListSchemaNode leafListSchema) {
            if (leafListSchema.isUserOrdered()) {
                return leafListSchema;
            }
            message = "Insert parameter can be used only with ordered-by user leaf-list.";
        } else {
            message = "Insert parameter can be used only with list or leaf-list";
        }
        throw new RequestException(ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT, message);
    }

    /**
     * Synchronize access to a path resource, translating any failure to a {@link RequestException}.
     *
     * @param <T> The type being accessed
     * @param future Access future
     * @param path Path being accessed
     * @return The accessed value
     * @throws RequestException if commit fails
     */
    // FIXME: require DatabindPath.Data here
    public static <T> T syncAccess(final ListenableFuture<T> future, final YangInstanceIdentifier path)
            throws RequestException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RequestException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestException("Interrupted while accessing " + path, e);
        }
    }

    public static @NonNull RequestException decodeException(final Throwable ex, final String txType,
            final Data dataPath) {
        if (ex instanceof RequestException requestException) {
            LOG.trace("Operation via Restconf transaction {} at path {} was not executed because of: {}",
                txType, dataPath.instance(), requestException.getMessage());
            return requestException;
        }
        if (ex instanceof NetconfDocumentedException netconfError) {
            return new RequestException(netconfError.getErrorType(), netconfError.getErrorTag(),
                netconfError.getMessage(), dataPath.toErrorPath(), ex);
        }
        if (ex instanceof TransactionCommitFailedException) {
            // If device send some error message we want this message to get to client and not just to throw it away
            // or override it with new generic message. We search for NetconfDocumentedException that was send from
            // netconfSB and we create RequestException accordingly.
            for (var error : Throwables.getCausalChain(ex)) {
                if (error instanceof NetconfDocumentedException netconfError) {
                    return new RequestException(netconfError.getErrorType(), netconfError.getErrorTag(),
                        netconfError.getMessage(), dataPath.toErrorPath(), ex);
                }
                if (error instanceof DocumentedException documentedError) {
                    final var errorTag = documentedError.getErrorTag();
                    if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} already exists",
                            dataPath.instance());
                        return new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, "Data already exists",
                            dataPath.toErrorPath(), ex);
                    } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                        LOG.trace("Operation via Restconf was not executed because data at {} does not exist",
                            dataPath.instance());
                        return new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                            "Data does not exist", dataPath.toErrorPath(), ex);
                    }
                }
            }

            return new RequestException("Transaction(" + txType + ") not committed correctly", ex);
        }

        return new RequestException("Transaction(" + txType + ") failed", ex);
    }
}
