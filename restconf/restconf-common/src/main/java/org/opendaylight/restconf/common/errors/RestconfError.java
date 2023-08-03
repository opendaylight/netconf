/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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
public class RestconfError implements Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

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
        this.errorType = requireNonNull(errorType, "Error type is required for RestConfError");
        this.errorTag = requireNonNull(errorTag, "Error tag is required for RestConfError");
        this.errorMessage = errorMessage;
        this.errorAppTag = errorAppTag;
        this.errorInfo = errorInfo;
        this.errorPath = errorPath;
    }

    /**
     * Constructs a RestConfError object from an RpcError.
     */
    public RestconfError(final RpcError rpcError) {

        errorType = rpcError.getErrorType();

        final ErrorTag tag = rpcError.getTag();
        errorTag = tag != null ? tag : ErrorTag.OPERATION_FAILED;

        errorMessage = rpcError.getMessage();
        errorAppTag = rpcError.getApplicationTag();

        String localErrorInfo = null;
        if (rpcError.getInfo() == null) {
            if (rpcError.getCause() != null) {
                localErrorInfo = rpcError.getCause().getMessage();
            } else if (rpcError.getSeverity() != null) {
                localErrorInfo = "<severity>" + rpcError.getSeverity().elementBody() + "</severity>";
            }
        } else {
            localErrorInfo = rpcError.getInfo();
        }

        errorInfo = localErrorInfo;
        errorPath = null;
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
                + "error-type: " + errorType.elementBody() + ", error-tag: " + errorTag.elementBody()
                + (errorAppTag != null ? ", error-app-tag: " + errorAppTag : "")
                + (errorMessage != null ? ", error-message: " + errorMessage : "")
                + (errorInfo != null ? ", error-info: " + errorInfo : "")
                + (errorPath != null ? ", error-path: " + errorPath.toString() : "")
                + "]";
    }
}
