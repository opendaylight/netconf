/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangNetconfError;
import org.opendaylight.yangtools.yang.data.api.YangNetconfErrorAware;

/**
 * Unchecked exception to communicate error information, as defined in the ietf restcong draft, to be sent to the
 * client.
 *
 * <p>
 * See also <a href="https://tools.ietf.org/html/draft-bierman-netconf-restconf-02">RESTCONF</a>
 *
 * @author Devin Avery
 * @author Thomas Pantelis
 */
public class RestconfDocumentedException extends WebApplicationException {
    @Serial
    private static final long serialVersionUID = 2L;

    private final List<RestconfError> errors;
    private final Status status;

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
     * @param message
     *            A string which provides a plain text string describing the error.
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param cause
     *            The underlying exception cause.
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                       final Throwable cause) {
        this(cause, new RestconfError(errorType, errorTag, message, null, cause.getMessage(), null));
    }

    /**
     * Constructs an instance with an error message, error type, and error tag.
     *
     * @param message
     *            A string which provides a plain text string describing the error.
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag) {
        this(null, new RestconfError(errorType, errorTag, message));
    }

    /**
     * Constructs an instance with an error message, error type, error tag and error path.
     *
     * @param message
     *            A string which provides a plain text string describing the error.
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorPath
     *            The instance identifier representing error path
     */
    public RestconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                       final YangInstanceIdentifier errorPath) {
        this(null, new RestconfError(errorType, errorTag, message, errorPath));
    }

    /**
     * Constructs an instance with an error message and exception cause.
     * The underlying exception is included in the error-info.
     *
     * @param message
     *            A string which provides a plain text string describing the error.
     * @param cause
     *            The underlying exception cause.
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
        if (!errors.isEmpty()) {
            this.errors = List.copyOf(errors);
        } else {
            this.errors = List.of(new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, message));
        }

        status = null;
    }

    /**
     * Constructs an instance with the given RpcErrors.
     */
    public RestconfDocumentedException(final String message, final Throwable cause,
                                       final Collection<? extends RpcError> rpcErrors) {
        this(message, cause, convertToRestconfErrors(rpcErrors));
    }

    /**
     * Constructs an instance with an HTTP status and no error information.
     *
     * @param status
     *            the HTTP status.
     */
    public RestconfDocumentedException(final Status status) {
        errors = List.of();
        this.status = requireNonNull(status, "Status can't be null");
    }

    public RestconfDocumentedException(final Throwable cause, final RestconfError error) {
        super(cause, ErrorTags.statusOf(error.getErrorTag()));
        errors = List.of(error);
        status = null;
    }

    public RestconfDocumentedException(final Throwable cause, final List<RestconfError> errors) {
        super(cause, ErrorTags.statusOf(errors.get(0).getErrorTag()));
        this.errors = List.copyOf(errors);
        status = null;
    }

    public static RestconfDocumentedException decodeAndThrow(final String message,
            final OperationFailedException cause) {
        for (final RpcError error : cause.getErrorList()) {
            if (error.getErrorType() == ErrorType.TRANSPORT && error.getTag().equals(ErrorTag.RESOURCE_DENIED)) {
                throw new RestconfDocumentedException(error.getMessage(), ErrorType.TRANSPORT,
                    ErrorTags.RESOURCE_DENIED_TRANSPORT, cause);
            }
        }
        throw new RestconfDocumentedException(message, cause, cause.getErrorList());
    }

    /**
     * Throw an instance of this exception if an expression evaluates to true. If the expression evaluates to false,
     * this method does nothing.
     *
     * @param expression Expression to be evaluated
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     * @param format Format string, according to {@link String#format(String, Object...)}.
     * @param args Format string arguments, according to {@link String#format(String, Object...)}
     * @throws RestconfDocumentedException if the expression evaluates to true.
     */
    public static void throwIf(final boolean expression, final ErrorType errorType, final ErrorTag errorTag,
            final @NonNull String format, final Object... args) {
        if (expression) {
            throw new RestconfDocumentedException(String.format(format, args), errorType, errorTag);
        }
    }

    /**
     * Throw an instance of this exception if an expression evaluates to true. If the expression evaluates to false,
     * this method does nothing.
     *
     * @param expression Expression to be evaluated
     * @param message error message
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     * @throws RestconfDocumentedException if the expression evaluates to true.
     */
    public static void throwIf(final boolean expression, final @NonNull String message,
            final ErrorType errorType, final ErrorTag errorTag) {
        if (expression) {
            throw new RestconfDocumentedException(message, errorType, errorTag);
        }
    }

    /**
     * Throw an instance of this exception if an object is null. If the object is non-null, it will
     * be returned as the result of this method.
     *
     * @param obj Object reference to be checked
     * @param errorType The enumerated type indicating the layer where the error occurred.
     * @param errorTag The enumerated tag representing a more specific error cause.
     * @param format Format string, according to {@link String#format(String, Object...)}.
     * @param args Format string arguments, according to {@link String#format(String, Object...)}
     * @throws RestconfDocumentedException if the expression evaluates to true.
     */
    public static <T> @NonNull T throwIfNull(final @Nullable T obj, final ErrorType errorType, final ErrorTag errorTag,
            final @NonNull String format, final Object... args) {
        if (obj == null) {
            throw new RestconfDocumentedException(String.format(format, args), errorType, errorTag);
        }
        return obj;
    }

    /**
     * Throw an instance of this exception if the specified exception has a {@link YangNetconfError} attachment.
     *
     * @param cause Proposed cause of a RestconfDocumented exception
     */
    public static void throwIfYangError(final Throwable cause) {
        if (cause instanceof YangNetconfErrorAware infoAware) {
            throw new RestconfDocumentedException(cause, infoAware.getNetconfErrors().stream()
                .map(error -> new RestconfError(error.type(), error.tag(), error.message(), error.appTag(),
                    // FIXME: pass down error info
                    null, error.path()))
                .toList());
        }
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

    public List<RestconfError> getErrors() {
        return errors;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return "errors: " + errors + (status != null ? ", status: " + status : "");
    }
}
