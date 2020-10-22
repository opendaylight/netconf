/*
 * Copyright (c) 2020 ... . and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator.util;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Map.Entry;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.FailedNetconfMessage;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public final class NativeNetconfMessageUtil {

    public static final String MESSAGE_ID_ATTR = "message-id";

    private NativeNetconfMessageUtil() {
    }

    public static RpcResult<NetconfMessage> toRpcResult(final FailedNetconfMessage message) {
        return RpcResultBuilder.<NetconfMessage>failed()
                .withRpcError(toRpcError(new NetconfDocumentedException(message.getException().getMessage(),
                        DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.MALFORMED_MESSAGE,
                        DocumentedException.ErrorSeverity.ERROR)))
                .build();
    }

    public static RpcError toRpcError(final NetconfDocumentedException ex) {
        final StringBuilder infoBuilder = new StringBuilder();
        final Map<String, String> errorInfo = ex.getErrorInfo();
        if (errorInfo != null) {
            for (final Entry<String, String> e : errorInfo.entrySet()) {
                infoBuilder.append('<').append(e.getKey()).append('>').append(e.getValue()).append("</")
                        .append(e.getKey()).append('>');

            }
        }

        final ErrorSeverity severity = toRpcErrorSeverity(ex.getErrorSeverity());
        return severity == ErrorSeverity.ERROR
                ? RpcResultBuilder.newError(toRpcErrorType(ex.getErrorType()), ex.getErrorTag().getTagValue(),
                        ex.getLocalizedMessage(), null, infoBuilder.toString(), ex.getCause())
                : RpcResultBuilder.newWarning(toRpcErrorType(ex.getErrorType()), ex.getErrorTag().getTagValue(),
                        ex.getLocalizedMessage(), null, infoBuilder.toString(), ex.getCause());
    }

    private static ErrorSeverity toRpcErrorSeverity(final NetconfDocumentedException.ErrorSeverity severity) {
        switch (severity) {
            case WARNING:
                return RpcError.ErrorSeverity.WARNING;
            default:
                return RpcError.ErrorSeverity.ERROR;
        }
    }

    private static RpcError.ErrorType toRpcErrorType(final NetconfDocumentedException.ErrorType type) {
        switch (type) {
            case PROTOCOL:
                return RpcError.ErrorType.PROTOCOL;
            case RPC:
                return RpcError.ErrorType.RPC;
            case TRANSPORT:
                return RpcError.ErrorType.TRANSPORT;
            default:
                return RpcError.ErrorType.APPLICATION;
        }
    }

    public static void checkValidReply(final NetconfMessage input, final NetconfMessage output)
            throws NetconfDocumentedException {
        final String inputMsgId = input.getDocument().getDocumentElement().getAttribute(MESSAGE_ID_ATTR);
        final String outputMsgId = output.getDocument().getDocumentElement().getAttribute(MESSAGE_ID_ATTR);

        if (!inputMsgId.equals(outputMsgId)) {
            final Map<String, String> errorInfo = ImmutableMap.<String, String>builder()
                    .put("actual-message-id", outputMsgId).put("expected-message-id", inputMsgId).build();

            throw new NetconfDocumentedException("Response message contained unknown \"message-id\"", null,
                    NetconfDocumentedException.ErrorType.PROTOCOL, NetconfDocumentedException.ErrorTag.BAD_ATTRIBUTE,
                    NetconfDocumentedException.ErrorSeverity.ERROR, errorInfo);
        }
    }

    public static void checkSuccessReply(final NetconfMessage output) throws NetconfDocumentedException {
        if (NetconfMessageUtil.isErrorMessage(output)) {
            throw NetconfDocumentedException.fromXMLDocument(output.getDocument());
        }
    }
}
