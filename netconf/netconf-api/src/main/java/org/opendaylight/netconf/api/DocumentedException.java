/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.RPC_REPLY_KEY;
import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Checked exception to communicate an error that needs to be sent to the
 * netconf client.
 */
public class DocumentedException extends Exception {

    public static final String RPC_ERROR = "rpc-error";
    public static final String ERROR_TYPE = "error-type";
    public static final String ERROR_TAG = "error-tag";
    public static final String ERROR_SEVERITY = "error-severity";
    public static final String ERROR_APP_TAG = "error-app-tag";
    public static final String ERROR_PATH = "error-path";
    public static final String ERROR_MESSAGE = "error-message";
    public static final String ERROR_INFO = "error-info";

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DocumentedException.class);

    private static final DocumentBuilderFactory BUILDER_FACTORY;

    static {
        BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
        try {
            BUILDER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
            BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            BUILDER_FACTORY.setXIncludeAware(false);
            BUILDER_FACTORY.setExpandEntityReferences(false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        BUILDER_FACTORY.setNamespaceAware(true);
        BUILDER_FACTORY.setCoalescing(true);
        BUILDER_FACTORY.setIgnoringElementContentWhitespace(true);
        BUILDER_FACTORY.setIgnoringComments(true);
    }

    public enum ErrorTag {
        ACCESS_DENIED("access-denied"),
        BAD_ATTRIBUTE("bad-attribute"),
        BAD_ELEMENT("bad-element"),
        DATA_EXISTS("data-exists"),
        DATA_MISSING("data-missing"),
        IN_USE("in-use"),
        INVALID_VALUE("invalid-value"),
        LOCK_DENIED("lock-denied"),
        MALFORMED_MESSAGE("malformed-message"),
        MISSING_ATTRIBUTE("missing-attribute"),
        MISSING_ELEMENT("missing-element"),
        OPERATION_FAILED("operation-failed"),
        OPERATION_NOT_SUPPORTED("operation-not-supported"),
        RESOURCE_DENIED("resource-denied"),
        ROLLBCK_FAILED("rollback-failed"),
        TOO_BIG("too-big"),
        UNKNOWN_ATTRIBUTE("unknown-attribute"),
        UNKNOWN_ELEMENT("unknown-element"),
        UNKNOWN_NAMESPACE("unknown-namespace");

        private final String tagValue;

        ErrorTag(final String tagValue) {
            this.tagValue = tagValue;
        }

        public String getTagValue() {
            return this.tagValue;
        }

        public static ErrorTag from(final String text) {
            for (ErrorTag e : values()) {
                if (e.getTagValue().equals(text)) {
                    return e;
                }
            }

            return OPERATION_FAILED;
        }
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
        this.errorInfo = errorInfo;
    }

    public static <E extends Exception> DocumentedException wrap(final E exception) throws DocumentedException {
        final Map<String, String> errorInfo = new HashMap<>();
        errorInfo.put(ErrorTag.OPERATION_FAILED.name(), "Exception thrown");
        throw new DocumentedException(exception.getMessage(), exception, ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR, errorInfo);
    }

    public static DocumentedException fromXMLDocument(final Document fromDoc) {

        ErrorType errorType = ErrorType.APPLICATION;
        ErrorTag errorTag = ErrorTag.OPERATION_FAILED;
        ErrorSeverity errorSeverity = ErrorSeverity.ERROR;
        Map<String, String> errorInfo = null;
        String errorMessage = "";
        String allErrorMessages = "";

        Node rpcReply = fromDoc.getDocumentElement();

        // FIXME: BUG? - we only handle one rpc-error. For now, shove extra errorMessages
        // found in multiple rpc-error in the errorInfo Map to at least let them propagate
        // back to caller.
        int rpcErrorCount = 0;

        NodeList replyChildren = rpcReply.getChildNodes();
        for (int i = 0; i < replyChildren.getLength(); i++) {
            Node replyChild = replyChildren.item(i);
            if (RPC_ERROR.equals(replyChild.getLocalName())) {
                rpcErrorCount++;
                NodeList rpcErrorChildren = replyChild.getChildNodes();
                for (int j = 0; j < rpcErrorChildren.getLength(); j++) {
                    Node rpcErrorChild = rpcErrorChildren.item(j);

                    // FIXME: use a switch expression here
                    if (ERROR_TYPE.equals(rpcErrorChild.getLocalName())) {
                        final ErrorType type = ErrorType.forElementBody(rpcErrorChild.getTextContent());
                        // FIXME: this should be a hard error
                        errorType = type != null ? type : ErrorType.APPLICATION;
                    } else if (ERROR_TAG.equals(rpcErrorChild.getLocalName())) {
                        errorTag = ErrorTag.from(rpcErrorChild.getTextContent());
                    } else if (ERROR_SEVERITY.equals(rpcErrorChild.getLocalName())) {
                        final ErrorSeverity sev = ErrorSeverity.forElementBody(rpcErrorChild.getTextContent());
                        // FIXME: this should be a hard error
                        errorSeverity = sev != null ? sev : ErrorSeverity.ERROR;
                    } else if (ERROR_MESSAGE.equals(rpcErrorChild.getLocalName())) {
                        errorMessage = rpcErrorChild.getTextContent();
                        allErrorMessages = allErrorMessages + errorMessage;
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
            errorInfo.put("Multiple Errors Found", allErrorMessages);
        }

        return new DocumentedException(errorMessage, errorType, errorTag, errorSeverity, errorInfo);
    }

    private static Map<String, String> parseErrorInfo(final Node node) {
        Map<String, String> infoMap = new HashMap<>();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                infoMap.put(child.getNodeName(), child.getTextContent());
            }
        }

        return infoMap;
    }

    public ErrorType getErrorType() {
        return this.errorType;
    }

    public ErrorTag getErrorTag() {
        return this.errorTag;
    }

    public ErrorSeverity getErrorSeverity() {
        return this.errorSeverity;
    }

    public Map<String, String> getErrorInfo() {
        return this.errorInfo;
    }

    public Document toXMLDocument() {
        Document doc = null;
        try {
            doc = BUILDER_FACTORY.newDocumentBuilder().newDocument();

            Node rpcReply = doc.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, RPC_REPLY_KEY);
            doc.appendChild(rpcReply);

            Node rpcError = doc.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, RPC_ERROR);
            rpcReply.appendChild(rpcError);

            rpcError.appendChild(createTextNode(doc, ERROR_TYPE, getErrorType().elementBody()));
            rpcError.appendChild(createTextNode(doc, ERROR_TAG, getErrorTag().getTagValue()));
            rpcError.appendChild(createTextNode(doc, ERROR_SEVERITY, getErrorSeverity().elementBody()));
            rpcError.appendChild(createTextNode(doc, ERROR_MESSAGE, getLocalizedMessage()));

            Map<String, String> errorInfoMap = getErrorInfo();
            if (errorInfoMap != null && !errorInfoMap.isEmpty()) {
                /*
                 * <error-info> <bad-attribute>message-id</bad-attribute>
                 * <bad-element>rpc</bad-element> </error-info>
                 */

                Node errorInfoNode = doc.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, ERROR_INFO);
                errorInfoNode.setPrefix(rpcReply.getPrefix());
                rpcError.appendChild(errorInfoNode);

                for (Entry<String, String> entry : errorInfoMap.entrySet()) {
                    errorInfoNode.appendChild(createTextNode(doc, entry.getKey(), entry.getValue()));
                }
            }
        } catch (final ParserConfigurationException e) {
            // this shouldn't happen
            LOG.error("Error outputting to XML document", e);
        }

        return doc;
    }

    private Node createTextNode(final Document doc, final String tag, final String textContent) {
        Node node = doc.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, tag);
        node.setTextContent(textContent);
        return node;
    }

    @Override
    public String toString() {
        return "NetconfDocumentedException{" + "message=" + getMessage() + ", errorType=" + this.errorType
                + ", errorTag=" + this.errorTag + ", errorSeverity=" + this.errorSeverity + ", errorInfo="
                + this.errorInfo + '}';
    }
}
