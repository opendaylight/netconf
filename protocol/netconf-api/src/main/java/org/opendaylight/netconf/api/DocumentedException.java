/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.RPC_REPLY_KEY;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Checked exception to communicate an error that needs to be sent to the
 * netconf client.
 */
// FIXME: NETCONF-793: implement YangNetconfErrorAware
public class DocumentedException extends Exception {
    /**
     * The name of the <a href="https://www.rfc-editor.org/rfc/rfc6241#section-4.3">rpc-error</a> element of a
     * {@code rpc-reply} NETCONF message.
     */
    // FIXME: NETCONF-1014: These should reside in RpcErrorMessage, as it comes from rfc6241.xsd, which should be
    //                      provided with this package. We also should have a YANG model mirroring the RFC6241's
    //                      netconf.xsd. This makes for a strong binding to RFC6241, there is not a rfc6241-bis
    //                      in sight, so we should be just fine.
    public static final String RPC_ERROR = "rpc-error";
    public static final String ERROR_TYPE = "error-type";
    public static final String ERROR_TAG = "error-tag";
    public static final String ERROR_SEVERITY = "error-severity";
    public static final String ERROR_APP_TAG = "error-app-tag";
    public static final String ERROR_PATH = "error-path";
    public static final String ERROR_MESSAGE = "error-message";
    public static final String ERROR_INFO = "error-info";

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DocumentedException.class);
    private static final DocumentBuilderFactory BUILDER_FACTORY;

    static {
        final var bf = DocumentBuilderFactory.newInstance();

        try {
            bf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            bf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            bf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            bf.setXIncludeAware(false);
            bf.setExpandEntityReferences(false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        bf.setNamespaceAware(true);
        bf.setCoalescing(true);
        bf.setIgnoringElementContentWhitespace(true);
        bf.setIgnoringComments(true);

        BUILDER_FACTORY = bf;
    }

    private final ErrorType errorType;
    private final ErrorTag errorTag;
    private final ErrorSeverity errorSeverity;
    private final Map<String, String> errorInfo;

    public DocumentedException(final String message) {
        this(message, ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
    }

    public DocumentedException(final String message, final Exception cause) {
        this(message, cause, ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
    }

    public DocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
            final ErrorSeverity errorSeverity) {
        this(message, errorType, errorTag, errorSeverity, Map.of());
    }

    public DocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
            final ErrorSeverity errorSeverity, final Map<String, String> errorInfo) {
        super(message);
        this.errorType = errorType;
        this.errorTag = errorTag;
        this.errorSeverity = errorSeverity;
        this.errorInfo = errorInfo;
    }

    public DocumentedException(final String message, final Exception cause, final ErrorType errorType,
            final ErrorTag errorTag, final ErrorSeverity errorSeverity) {
        this(message, cause, errorType, errorTag, errorSeverity, Map.of());
    }

    public DocumentedException(final String message, final Exception cause, final ErrorType errorType,
            final ErrorTag errorTag, final ErrorSeverity errorSeverity, final Map<String, String> errorInfo) {
        super(message, cause);
        this.errorType = errorType;
        this.errorTag = errorTag;
        this.errorSeverity = errorSeverity;
        // FIXME: this contract (based on what fromXMLDocument does) is quite wrong. It ignores the XML realities of
        //        what constitutes a tag and especially tag value when faced with encoding XML-namespaced entities --
        //        such as 'identity' arguments -- represented as QNames.
        this.errorInfo = errorInfo;
    }

    public static DocumentedException wrap(final Exception exception) throws DocumentedException {
        throw new DocumentedException(exception.getMessage(), exception, ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR,
                Map.of(ErrorTag.OPERATION_FAILED.elementBody(), "Exception thrown"));
    }

    public static DocumentedException fromXMLDocument(final Document fromDoc) {
        var errorType = ErrorType.APPLICATION;
        var errorTag = ErrorTag.OPERATION_FAILED;
        var errorSeverity = ErrorSeverity.ERROR;
        Map<String, String> errorInfo = null;
        var errorMessage = "";
        final var allErrorMessages = new StringBuilder();

        final var rpcReply = fromDoc.getDocumentElement();

        // FIXME: we only handle one rpc-error. For now, shove extra errorMessages found in multiple rpc-error in the
        //        errorInfo Map to at least let them propagate back to caller.
        //        this will be solved through migration to YangNetconfErrorAware, as that allows reporting multipl
        //        error events
        int rpcErrorCount = 0;

        final var replyChildren = rpcReply.getChildNodes();
        for (int i = 0, ilen = replyChildren.getLength(); i < ilen; i++) {
            final var replyChild = replyChildren.item(i);
            if (RPC_ERROR.equals(replyChild.getLocalName())) {
                rpcErrorCount++;
                final var rpcErrorChildren = replyChild.getChildNodes();
                for (int j = 0, jlen = rpcErrorChildren.getLength(); j < jlen; j++) {
                    final var rpcErrorChild = rpcErrorChildren.item(j);

                    // FIXME: use a switch expression here
                    if (ERROR_TYPE.equals(rpcErrorChild.getLocalName())) {
                        final var type = ErrorType.forElementBody(rpcErrorChild.getTextContent());
                        // FIXME: this should be a hard error
                        errorType = type != null ? type : ErrorType.APPLICATION;
                    } else if (ERROR_TAG.equals(rpcErrorChild.getLocalName())) {
                        errorTag = new ErrorTag(rpcErrorChild.getTextContent());
                    } else if (ERROR_SEVERITY.equals(rpcErrorChild.getLocalName())) {
                        final var sev = ErrorSeverity.forElementBody(rpcErrorChild.getTextContent());
                        // FIXME: this should be a hard error
                        errorSeverity = sev != null ? sev : ErrorSeverity.ERROR;
                    } else if (ERROR_MESSAGE.equals(rpcErrorChild.getLocalName())) {
                        errorMessage = rpcErrorChild.getTextContent();
                        allErrorMessages.append(errorMessage);
                    } else if (ERROR_INFO.equals(rpcErrorChild.getLocalName())) {
                        errorInfo = parseErrorInfo(rpcErrorChild);
                    }
                }
            }
        }

        if (rpcErrorCount > 1) {
            if (errorInfo == null) {
                errorInfo = new HashMap<>();
            }
            errorInfo.put("Multiple Errors Found", allErrorMessages.toString());
        }

        return new DocumentedException(errorMessage, errorType, errorTag, errorSeverity, errorInfo);
    }

    private static Map<String, String> parseErrorInfo(final Node node) {
        final var infoMap = new HashMap<String, String>();
        final var children = node.getChildNodes();
        for (int i = 0, length = children.getLength(); i < length; i++) {
            final var child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                // FIXME: Holy namespace ignorance, Batman!
                //
                // So this is just not enough to decode things in the general sense. getTextContenxt() may easily be a
                // qualified QName, such as an identity name or an instance-identifier. What the entire 'infoMap' needs
                // to contain is each child's XML context, so that the string literal can be interpreted as needed.
                //
                // yang.common.YangNamespaceContext represents the minimal API surface that needs to be exposed. That
                // effectively means:
                //
                // final class ElementValue implements YangNamespaceContext {
                //   public final String elementContenxt();
                // }
                //
                // Map<QName, ElementValue> infoMap;
                //
                // except... what do we use for revision?
                infoMap.put(child.getNodeName(), child.getTextContent());
            }
        }

        return infoMap;
    }

    // FIXME: NETCONF-793: remove all of these in favor of YangNetconfErrorAware
    public ErrorType getErrorType() {
        return errorType;
    }

    public ErrorTag getErrorTag() {
        return errorTag;
    }

    public ErrorSeverity getErrorSeverity() {
        return errorSeverity;
    }

    public Map<String, String> getErrorInfo() {
        return errorInfo;
    }

    // FIXME: this really should be an spi/util method (or even netconf-util-w3c-dom?) as this certainly is not the
    //        primary interface we want to expose -- it is inherently mutable and its API is a pure nightmare.
    public Document toXMLDocument() {
        Document doc = null;
        try {
            doc = BUILDER_FACTORY.newDocumentBuilder().newDocument();

            final var rpcReply = doc.createElementNS(NamespaceURN.BASE, RPC_REPLY_KEY);
            doc.appendChild(rpcReply);

            final var rpcError = doc.createElementNS(NamespaceURN.BASE, RPC_ERROR);
            rpcReply.appendChild(rpcError);

            rpcError.appendChild(createTextNode(doc, ERROR_TYPE, getErrorType().elementBody()));
            rpcError.appendChild(createTextNode(doc, ERROR_TAG, getErrorTag().elementBody()));
            rpcError.appendChild(createTextNode(doc, ERROR_SEVERITY, getErrorSeverity().elementBody()));
            rpcError.appendChild(createTextNode(doc, ERROR_MESSAGE, getLocalizedMessage()));

            final var errorInfoMap = getErrorInfo();
            if (errorInfoMap != null && !errorInfoMap.isEmpty()) {
                /*
                 * <error-info> <bad-attribute>message-id</bad-attribute>
                 * <bad-element>rpc</bad-element> </error-info>
                 */
                final var errorInfoNode = doc.createElementNS(NamespaceURN.BASE, ERROR_INFO);
                errorInfoNode.setPrefix(rpcReply.getPrefix());
                rpcError.appendChild(errorInfoNode);

                for (var entry : errorInfoMap.entrySet()) {
                    errorInfoNode.appendChild(createTextNode(doc, entry.getKey(), entry.getValue()));
                }
            }
        } catch (final ParserConfigurationException e) {
            // this shouldn't happen
            LOG.error("Error outputting to XML document", e);
        }

        return doc;
    }

    private static Node createTextNode(final Document doc, final String tag, final String textContent) {
        final var node = doc.createElementNS(NamespaceURN.BASE, tag);
        node.setTextContent(textContent);
        return node;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper
            .add(ERROR_TYPE, errorType)
            .add(ERROR_TAG, errorTag)
            .add(ERROR_SEVERITY, errorSeverity)
            .add(ERROR_INFO, errorInfo)
            .add("message", getMessage());
    }
}
