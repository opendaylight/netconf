/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.messages.RpcMessage;
import org.opendaylight.netconf.api.messages.RpcReplyMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class AbstractNetconfOperation implements NetconfOperation {
    private final @NonNull SessionIdType sessionId;

    protected AbstractNetconfOperation(final SessionIdType sessionId) {
        this.sessionId = requireNonNull(sessionId);
    }

    public final @NonNull SessionIdType sessionId() {
        return sessionId;
    }

    @Override
    public HandlingPriority canHandle(final Document message) throws DocumentedException {
        OperationNameAndNamespace operationNameAndNamespace = null;
        operationNameAndNamespace = new OperationNameAndNamespace(message);
        return canHandle(operationNameAndNamespace.getOperationName(), operationNameAndNamespace.getNamespace());
    }

    protected HandlingPriority canHandle(final String operationName, final String operationNamespace) {
        return operationName.equals(getOperationName()) && operationNamespace.equals(getOperationNamespace())
                ? getHandlingPriority()
                : HandlingPriority.CANNOT_HANDLE;
    }

    public static final class OperationNameAndNamespace {
        private final String operationName;
        private final String namespace;
        private final XmlElement operationElement;

        public OperationNameAndNamespace(final Document message) throws DocumentedException {
            final var requestElement = getRequestElementWithCheck(message);
            operationElement = requestElement.getOnlyChildElement();
            operationName = operationElement.getName();
            namespace = operationElement.getNamespace();
        }

        public String getOperationName() {
            return operationName;
        }

        public String getNamespace() {
            return namespace;
        }

        public XmlElement getOperationElement() {
            return operationElement;
        }
    }

    protected static XmlElement getRequestElementWithCheck(final Document message) throws DocumentedException {
        return XmlElement.fromDomElementWithExpected(message.getDocumentElement(), RpcMessage.ELEMENT_NAME,
            NamespaceURN.BASE);
    }

    protected HandlingPriority getHandlingPriority() {
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY;
    }

    protected String getOperationNamespace() {
        return NamespaceURN.BASE;
    }

    protected abstract String getOperationName();

    @Override
    public Document handle(final Document requestMessage,
            final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
        final var requestElement = getRequestElementWithCheck(requestMessage);
        final var document = XmlUtil.newDocument();
        final var operationElement = requestElement.getOnlyChildElement();
        final var attributes = requestElement.getAttributes();

        final var response = handle(document, operationElement, subsequentOperation);
        final var rpcReply = XmlUtil.createElement(document, RpcReplyMessage.ELEMENT_NAME,
            Optional.of(NamespaceURN.BASE));
        if (XmlElement.fromDomElement(response).hasNamespace()) {
            rpcReply.appendChild(response);
        } else {
            final var responseNS = XmlUtil.createElement(document, response.getNodeName(),
                    Optional.of(NamespaceURN.BASE));
            final var list = response.getChildNodes();
            while (list.getLength() != 0) {
                responseNS.appendChild(list.item(0));
            }
            rpcReply.appendChild(responseNS);
        }

        for (var attribute : attributes.values()) {
            rpcReply.setAttributeNode((Attr) document.importNode(attribute, true));
        }
        document.appendChild(rpcReply);
        return document;
    }

    protected abstract Element handle(Document document, XmlElement message,
                                      NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException;

    @Override
    public String toString() {
        final var sb = new StringBuilder(getClass().getName());
        try {
            sb.append("{name=").append(getOperationName());
        } catch (UnsupportedOperationException e) {
            // no problem
        }
        return sb
            .append(", namespace=").append(getOperationNamespace())
            .append(", session=").append(sessionId.getValue())
            .append('}')
            .toString();
    }
}
