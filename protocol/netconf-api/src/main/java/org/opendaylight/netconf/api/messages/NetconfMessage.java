/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static java.util.Objects.requireNonNull;

import java.io.StringWriter;
import java.util.Map;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

/**
 * NetconfMessage represents a wrapper around {@link Document}.
 */
public class NetconfMessage {
    private static final Transformer TRANSFORMER;
    private static final TransformerProvider PROVIDER = new TransformerProvider();

    static {
        final Transformer t;
        try {
            t = XmlUtil.newIndentingTransformer();
        } catch (TransformerConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        TRANSFORMER = t;
    }

    private final @NonNull Document document;

    public NetconfMessage(final Document document) {
        this.document = requireNonNull(document);
    }

    /**
     * Create a new {@link NetconfMessage} based on supplied document.
     *
     * @param document A {@link Document} backing the message
     * @return A {@link NetconfMessage}
     * @throws NullPointerException if {@code document} is {@code null}
     * @throws DocumentedException if the {@code document} does not match a known {@link NetconfMessage}
     */
    public static @NonNull NetconfMessage of(final Document document) throws DocumentedException {
        final var root = document.getDocumentElement();
        final var rootName = root.getLocalName();
        final var rootNs = root.getNamespaceURI();

        if (rootNs != null) {
            switch (rootNs) {
                case NamespaceURN.BASE:
                    switch (rootName) {
                        case HelloMessage.ELEMENT_NAME:
                            return new HelloMessage(document);
                        case RpcMessage.ELEMENT_NAME:
                            return RpcMessage.ofChecked(document);
                        case RpcReplyMessage.ELEMENT_NAME:
                            return new RpcReplyMessage(document);
                        default:
                            break;
                    }
                    break;
                case NamespaceURN.NOTIFICATION:
                    switch (rootName) {
                        case NotificationMessage.ELEMENT_NAME:
                            return NotificationMessage.ofChecked(document);
                        default:
                            break;
                    }
                    break;
                default:
                    throw new DocumentedException("Unhandled namespace " + rootNs, ErrorType.PROTOCOL,
                        ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR, Map.of("bad-element", rootName));

            }
        } else if (HelloMessage.ELEMENT_NAME.equals(rootName)) {
            // accept even if hello has no namespace
            return new HelloMessage(document);
        }
        throw new DocumentedException("Unknown element " + rootName, ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
            ErrorSeverity.ERROR, Map.of("bad-element", rootName));
    }

    public final @NonNull Document getDocument() {
        return document;
    }

    @Override
    public final String toString() {
        final var result = new StreamResult(new StringWriter());
        final var source = new DOMSource(document.getDocumentElement());

        try {
            // Slight critical section is a tradeoff. This should be reasonably fast.
//            synchronized (TRANSFORMER) {
//                TRANSFORMER.transform(source, result);
//            }

            PROVIDER.transform(source,result);

        } catch (TransformerException e) {
            throw new IllegalStateException("Failed to encode document", e);
        }

        return result.getWriter().toString();
    }
}
