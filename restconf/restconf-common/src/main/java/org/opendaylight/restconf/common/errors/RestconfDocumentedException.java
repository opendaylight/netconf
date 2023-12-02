/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Unchecked exception to communicate error information, as defined
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.9">"errors" YANG Data Template</a>.
 *
 * @author Devin Avery
 * @author Thomas Pantelis
 */
public class RestconfDocumentedException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 3L;

    private final List<RestconfError> errors;

    // FIXME: this field should be non-null
    private final transient @Nullable EffectiveModelContext modelContext;

    /**
     * Constructs an instance with an error message. The error type defaults to APPLICATION and the error tag defaults
     * to OPERATION_FAILED.
     *
     * @param message
     *            A string which provides a plain text string describing the error.
     */
    public RestconfDocumentedException(final String message) {
        this(message, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    /**
     * Constructs an instance with an error message, error type, error tag and exception cause.
     *
     * @param message A string which provides a plain text string describing the error.
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     * @param cause The underlying exception cause.
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                       final Throwable cause) {
        this(cause, new RestconfError(errorType, errorTag, message, null, cause.getMessage(), null));
    }

    /**
     * Constructs an instance with an error message, error type, and error tag.
     *
     * @param message A string which provides a plain text string describing the error.
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag) {
        this(new RestconfError(errorType, errorTag, message));
    }

    /**
     * Constructs an instance with an error message, error type, error tag and error path.
     *
     * @param message A string which provides a plain text string describing the error.
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     * @param errorPath The instance identifier representing error path
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                       final YangInstanceIdentifier errorPath) {
        this(new RestconfError(errorType, errorTag, message, errorPath));
    }

    /**
     * Constructs an instance with an error message and exception cause.
     * The underlying exception is included in the error-info.
     *
     * @param message A string which provides a plain text string describing the error.
     * @param cause The underlying exception cause.
     */
    public RestconfDocumentedException(final String message, final Throwable cause) {
        this(cause, new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, message, null,
            cause.getMessage(), null));
    }

    /**
     * Constructs an instance with the given error.
     */
    public RestconfDocumentedException(final RestconfError error) {
        this(null, error);
    }

    /**
     * Constructs an instance with the given errors.
     */
    public RestconfDocumentedException(final String message, final Throwable cause, final List<RestconfError> errors) {
        // FIXME: We override getMessage so supplied message is lost for any public access
        // this was lost also in original code.
        super(cause);
        if (errors.isEmpty()) {
            this.errors = List.of(new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, message));
        } else {
            this.errors = List.copyOf(errors);
        }

        modelContext = null;
    }

    /**
     * Constructs an instance with the given RpcErrors.
     */
    public RestconfDocumentedException(final String message, final Throwable cause,
                                       final Collection<? extends RpcError> rpcErrors) {
        this(message, cause, convertToRestconfErrors(rpcErrors));
    }

    private static List<RestconfError> convertToRestconfErrors(final Collection<? extends RpcError> rpcErrors) {
        if (rpcErrors == null || rpcErrors.isEmpty()) {
            return List.of();
        }

        final var errorList = new ArrayList<RestconfError>();
        for (var rpcError : rpcErrors) {
            errorList.add(new RestconfError(rpcError));
        }
        return errorList;
    }

    public RestconfDocumentedException(final Throwable cause, final RestconfError error) {
        super(cause);
        errors = List.of(error);
        modelContext = null;
    }

    public RestconfDocumentedException(final Throwable cause, final RestconfError error,
            final EffectiveModelContext modelContext) {
        super(cause);
        errors = List.of(error);
        this.modelContext = requireNonNull(modelContext);
    }

    public RestconfDocumentedException(final Throwable cause, final List<RestconfError> errors) {
        super(cause);
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("At least one error is required");
        }
        this.errors = List.copyOf(errors);
        modelContext = null;
    }

    @Override
    public String getMessage() {
        return "errors: " + errors;
    }

    /**
     * Reference to {@link EffectiveModelContext} in which this exception was generated. This method will return
     * {@code null} if this exception was serialized or if the context is not available.
     *
     * @return Reference model context
     */
    public @Nullable EffectiveModelContext modelContext() {
        return modelContext;
    }

    public List<RestconfError> getErrors() {
        return errors;
    }
}
