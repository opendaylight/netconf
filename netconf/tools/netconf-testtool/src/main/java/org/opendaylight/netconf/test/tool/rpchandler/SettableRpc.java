/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpchandler;

import java.util.Optional;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

/**
 * {@link NetconfOperation} implementation. It can be configured to intercept rpcs with defined input
 * and reply with defined output. If input isn't defined, rpc handling is delegated to the subsequent
 * {@link NetconfOperation} which is able to handle it.
 */
class SettableRpc implements NetconfOperation {
    private final RpcHandler rpcHandler;

    SettableRpc(final RpcHandler rpcHandler) {
        this.rpcHandler = rpcHandler;
    }

    @Override
    public HandlingPriority canHandle(final Document message) {
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY.increasePriority(1000);
    }

    @Override
    public Document handle(final Document requestMessage, final NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException {
        final XmlElement requestElement = XmlElement.fromDomDocument(requestMessage);
        final XmlElement rpcElement = requestElement.getOnlyChildElement();
        final String msgId = requestElement.getAttribute(XmlNetconfConstants.MESSAGE_ID);
        final Optional<Document> response = rpcHandler.getResponse(rpcElement);
        if (response.isPresent()) {
            final Document document = response.orElseThrow();
            checkForError(document);
            document.getDocumentElement().setAttribute(XmlNetconfConstants.MESSAGE_ID, msgId);
            return document;
        } else if (subsequentOperation.isExecutionTermination()) {
            throw new DocumentedException("Mapping not found " + XmlUtil.toString(requestMessage),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        } else {
            return subsequentOperation.execute(requestMessage);
        }
    }

    private static void checkForError(final Document document) throws DocumentedException {
        final XmlElement rpcReply = XmlElement.fromDomDocument(document);
        if (rpcReply.getOnlyChildElementOptionally("rpc-error").isPresent()) {
            throw DocumentedException.fromXMLDocument(document);
        }
    }
}
