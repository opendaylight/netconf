/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Encapsulates a restconf error as defined in the ietf restconf draft.
 *
 * <br>
 * <br>
 * <b>Note:</b> Enumerations defined within are provided by the ietf restconf draft.
 *
 * @author Devin Avery
 *     See also <a href="https://tools.ietf.org/html/draft-bierman-netconf-restconf-02">RESTCONF</a>.
 */
public class RestconfError {

    public enum ErrorType {
        /**
         * Errors relating to the transport layer.
         */
        TRANSPORT,
        /**
         * Errors relating to the RPC or notification layer.
         */
        RPC,
        /**
         * Errors relating to the protocol operation layer.
         */
        PROTOCOL,
        /**
         * Errors relating to the server application layer.
         */
        APPLICATION;

        public String getErrorTypeTag() {
            return name().toLowerCase();
        }

        public static ErrorType valueOfCaseInsensitive(final String value) {
            try {
                return ErrorType.valueOf(ErrorType.class, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return APPLICATION;
            }
        }
    }

    public enum ErrorTag {
        IN_USE("in-use", 409 /* Conflict */),
        INVALID_VALUE("invalid-value", 400 /* Bad Request */),
        TOO_BIG("too-big", 413 /* Request Entity Too Large */),
        MISSING_ATTRIBUTE("missing-attribute", 400 /* Bad Request */),
        BAD_ATTRIBUTE("bad-attribute", 400 /* Bad Request */),
        UNKNOWN_ATTRIBUTE("unknown-attribute", 400 /* Bad Request */),
        MISSING_ELEMENT("missing-element", 400 /* Bad Request */),
        BAD_ELEMENT("bad-element", 400 /* Bad Request */),
        UNKNOWN_ELEMENT("unknown-element", 400 /* Bad Request */),
        UNKNOWN_NAMESPACE("unknown-namespace", 400 /* Bad Request */),
        ACCESS_DENIED("access-denied", 403 /* Forbidden */),
        LOCK_DENIED("lock-denied", 409 /* Conflict */),
        RESOURCE_DENIED("resource-denied", 409 /* Conflict */),
        ROLLBACK_FAILED("rollback-failed", 500 /* INTERNAL_SERVER_ERROR */),
        DATA_EXISTS("data-exists", 409 /* Conflict */),
        DATA_MISSING("data-missing", 404 /* Resource Not Found */),
        OPERATION_NOT_SUPPORTED("operation-not-supported", 501 /* Not Implemented */),
        OPERATION_FAILED("operation-failed", 500 /* INTERNAL_SERVER_ERROR */),
        PARTIAL_OPERATION("partial-operation", 500 /* INTERNAL_SERVER_ERROR */),
        MALFORMED_MESSAGE("malformed-message", 400 /* Bad Request */),
        RESOURCE_DENIED_TRANSPORT("resource-denied-transport", 503 /* Service Unavailable */);

        private final String tagValue;
        private final int statusCode;

        ErrorTag(final String tagValue, final int statusCode) {
            this.tagValue = tagValue;
            this.statusCode = statusCode;
        }

        public String getTagValue() {
            return this.tagValue.toLowerCase();
        }

        public static ErrorTag valueOfCaseInsensitive(final String value) {
            try {
                return ErrorTag.valueOf(ErrorTag.class, value.toUpperCase().replaceAll("-", "_"));
            } catch (IllegalArgumentException e) {
                return OPERATION_FAILED;
            }
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    private final ErrorType errorType;
    private final ErrorTag errorTag;
    private final String errorInfo;
    private final String errorAppTag;
    private final String errorMessage;
    private final YangInstanceIdentifier errorPath;

    /**
     * Constructs a RestConfError.
     *
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorMessage
     *            A string which provides a plain text string describing the error.
     */
    public RestconfError(final ErrorType errorType, final ErrorTag errorTag, final String errorMessage) {
        this(errorType, errorTag, errorMessage, null, null, null);
    }

    /**
     * Constructs a RestConfError object.
     *
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorMessage
     *            A string which provides a plain text string describing the error.
     * @param errorAppTag
     *            A string which represents an application-specific error tag that further specifies the error cause.
     */
    public RestconfError(final ErrorType errorType, final ErrorTag errorTag, final String errorMessage,
                         final String errorAppTag) {
        this(errorType, errorTag, errorMessage, errorAppTag, null, null);
    }

    /**
     * Constructs a RestConfError object.
     *
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorMessage
     *            A string which provides a plain text string describing the error.
     * @param errorPath
     *            An instance identifier which contains error path
     */
    public RestconfError(final ErrorType errorType, final ErrorTag errorTag, final String errorMessage,
                         final YangInstanceIdentifier errorPath) {
        this(errorType, errorTag, errorMessage, null, null, errorPath);
    }

    /**
     * Constructs a RestConfError object.
     *
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorMessage
     *            A string which provides a plain text string describing the error.
     * @param errorAppTag
     *            A string which represents an application-specific error tag that further specifies the error cause.
     * @param errorInfo
     *            A string, <b>formatted as XML</b>, which contains additional error information.
     */
    public RestconfError(final ErrorType errorType, final ErrorTag errorTag, final String errorMessage,
                         final String errorAppTag, final String errorInfo) {
        this(errorType, errorTag, errorMessage, errorAppTag, errorInfo, null);
    }

    /**
     * Constructs a RestConfError object.
     *
     * @param errorType
     *            The enumerated type indicating the layer where the error occurred.
     * @param errorTag
     *            The enumerated tag representing a more specific error cause.
     * @param errorMessage
     *            A string which provides a plain text string describing the error.
     * @param errorAppTag
     *            A string which represents an application-specific error tag that further specifies the error cause.
     * @param errorInfo
     *            A string, <b>formatted as XML</b>, which contains additional error information.
     * @param errorPath
     *            An instance identifier which contains error path
     */
    public RestconfError(final ErrorType errorType, final ErrorTag errorTag, final String errorMessage,
                         final String errorAppTag, final String errorInfo, final YangInstanceIdentifier errorPath) {
        Preconditions.checkNotNull(errorType, "Error type is required for RestConfError");
        Preconditions.checkNotNull(errorTag, "Error tag is required for RestConfError");
        this.errorType = errorType;
        this.errorTag = errorTag;
        this.errorMessage = errorMessage;
        this.errorAppTag = errorAppTag;
        this.errorInfo = errorInfo;
        this.errorPath = errorPath;
    }

    /**
     * Constructs a RestConfError object from an RpcError.
     */
    public RestconfError(final RpcError rpcError) {

        this.errorType = rpcError.getErrorType() == null ? ErrorType.APPLICATION : ErrorType
                .valueOfCaseInsensitive(rpcError.getErrorType().name());

        this.errorTag = rpcError.getTag() == null ? ErrorTag.OPERATION_FAILED : ErrorTag
                .valueOfCaseInsensitive(rpcError.getTag().toString());

        this.errorMessage = rpcError.getMessage();
        this.errorAppTag = rpcError.getApplicationTag();

        String errorInfo = null;
        if (rpcError.getInfo() == null) {
            if (rpcError.getCause() != null) {
                errorInfo = Throwables.getStackTraceAsString(rpcError.getCause());
            } else if (rpcError.getSeverity() != null) {
                errorInfo = "<severity>" + rpcError.getSeverity().toString().toLowerCase() + "</severity>";
            }
        } else {
            errorInfo = rpcError.getInfo();
        }

        this.errorInfo = errorInfo;
        this.errorPath = null;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public ErrorTag getErrorTag() {
        return errorTag;
    }

    public String getErrorInfo() {
        return errorInfo;
    }

    public String getErrorAppTag() {
        return errorAppTag;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public YangInstanceIdentifier getErrorPath() {
        return errorPath;
    }

    @Override
    public String toString() {
        return "RestconfError ["
                + "error-type: " + errorType.getErrorTypeTag() + ", error-tag: " + errorTag.getTagValue()
                + (errorAppTag != null ? ", error-app-tag: " + errorAppTag : "")
                + (errorMessage != null ? ", error-message: " + errorMessage : "")
                + (errorInfo != null ? ", error-info: " + errorInfo : "")
                + (errorPath != null ? ", error-path: " + errorPath.toString() : "")
                + "]";
    }
}
