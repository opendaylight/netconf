/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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
    public final HandlingPriority canHandle(final Document message) throws DocumentedException {
        final var operationNameAndNamespace = new OperationNameAndNamespace(message);
        return canHandle(operationNameAndNamespace.getOperationName(), operationNameAndNamespace.getNamespace());
    }

    protected HandlingPriority canHandle(final String operationName, final String operationNamespace) {
        return operationName.equals(getOperationName()) && operationNamespace.equals(getOperationNamespace())
            ? getHandlingPriority() : null;
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

    protected @NonNull HandlingPriority getHandlingPriority() {
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY;
    }

    protected String getOperationNamespace() {
        return NamespaceURN.BASE;
    }

    protected abstract String getOperationName();

    @Override
    public Document handle(final Document requestMessage, final NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException {
        final var requestElement = getRequestElementWithCheck(requestMessage);
        final var document = XmlUtil.newDocument();
        final var operationElement = requestElement.getOnlyChildElement();
        final var attributes = requestElement.getAttributes();

        final var response = handle(document, operationElement, subsequentOperation);
        final var rpcReply = document.createElementNS(NamespaceURN.BASE, RpcReplyMessage.ELEMENT_NAME);
        if (XmlUtil.hasNamespace(response)) {
            rpcReply.appendChild(response);
        } else {
            // FIXME: use getLocalName() instead
            final var responseNS = document.createElementNS(NamespaceURN.BASE, response.getNodeName());
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
        @Nullable NetconfOperationChainedExecution subsequentOperation) throws DocumentedException;

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
