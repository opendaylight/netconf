/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.odlparent.logging.markers.Markers;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class XmlUtil {
    private static final Logger LOG = LoggerFactory.getLogger(XmlUtil.class);
    /**
     * A pre-compiled XSL template to deal with Java XML transform creating empty lines when indenting is enabled, as
     * detailed in <a href="https://bugs.openjdk.org/browse/JDK-8262285">JDK-8262285</a>.
     */
    private static final Templates PRETTY_PRINT_TEMPLATE;

    static {
        try {
            PRETTY_PRINT_TEMPLATE = TransformerFactory.newInstance()
                .newTemplates(new StreamSource(Resources.getResource(XmlUtil.class, "/pretty-print.xsl").openStream()));
        } catch (IOException | TransformerConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final DocumentBuilderFactory BUILDER_FACTORY;

    static {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // Performance improvement for messages with size <10k according to
            // https://xerces.apache.org/xerces2-j/faq-performance.html
            factory.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        BUILDER_FACTORY = factory;
    }

    private static final ThreadLocal<DocumentBuilder> DEFAULT_DOM_BUILDER = new ThreadLocal<>() {
        @Override
        protected DocumentBuilder initialValue() {
            try {
                return BUILDER_FACTORY.newDocumentBuilder();
            } catch (final ParserConfigurationException e) {
                throw new IllegalStateException("Failed to create threadLocal dom builder", e);
            }
        }

        @Override
        public void set(final DocumentBuilder value) {
            throw new UnsupportedOperationException();
        }
    };

    private XmlUtil() {
        // Hidden on purpose
    }

    public static boolean hasNamespace(final Element element) {
        return namespaceAttribute(element) != null || namespace(element) != null;
    }

    public static @Nullable String namespace(final Element element) {
        final var namespaceURI = element.getNamespaceURI();
        return namespaceURI == null || namespaceURI.isEmpty() ? null : namespaceURI;
    }

    /**
     * Wrapper around {@link Element#getElementsByTagNameNS(String, String)} which ensures there is exactly one match.
     *
     * @param element Parent element
     * @param namespace child element namespace
     * @param localName child element local name
     * @return The single matching element
     * @throws DocumentedException if there is not exactly one match
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if either {@code namespace} or {@code localName} is not acceptable
     */
    public static @NonNull Element requireSingleElement(final Element element, final String namespace,
            final String localName) throws DocumentedException {
        if (namespace.equals("*")) {
            throw new IllegalArgumentException("Unsupported wildcard namespace");
        }
        if (localName.equals("*")) {
            throw new IllegalArgumentException("Unsupported wildcard localName");
        }

        final var elements = element.getElementsByTagNameNS(namespace, localName);
        // Be careful about NodeList expansion
        final var first = (Element) elements.item(0);
        if (first == null) {
            if (LOG.isDebugEnabled(Markers.confidential())) {
                LOG.debug(Markers.confidential(), "{}:{} not present in {}", namespace, localName, toString(element));
            }
            throw new DocumentedException("Expected %s:%s once, not found".formatted(namespace, localName),
                // as per https://www.rfc-editor.org/rfc/rfc6241#page-77:
                //                error-tag:      missing-element
                //                error-type:     protocol, application
                //                error-severity: error
                //                error-info:     <bad-element> : name of the missing element
                //                Description:    An expected element is missing.
                ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT, ErrorSeverity.ERROR,
                Map.of("bad-element", localName));
        }
        final var second = (Element) elements.item(1);
        if (second != null) {
            if (LOG.isDebugEnabled(Markers.confidential())) {
                LOG.debug(Markers.confidential(), "{}:{} is present multiple times in {}", namespace, localName,
                    toString(element));
            }
            throw new DocumentedException("Expected %s:%s once, found at least twice".formatted(namespace, localName),
                // as per https://www.rfc-editor.org/rfc/rfc6241#page-77:
                //              error-tag:      unknown-element
                //              error-type:     protocol, application
                //              error-severity: error
                //              error-info:     <bad-element> : name of the unexpected element
                //              Description:    An unexpected element is present.
                ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT, ErrorSeverity.ERROR,
                Map.of("bad-element", localName));
        }
        return first;
    }

    static @Nullable String namespaceAttribute(final Element element) {
        final var attribute = element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE);
        return attribute.isEmpty() ? null : attribute;
    }

    public static Element readXmlToElement(final File xmlFile) throws SAXException, IOException {
        return readXmlToElement(xmlFile.toPath());
    }

    public static Element readXmlToElement(final Path xmlFile) throws SAXException, IOException {
        return readXmlToDocument(Files.newInputStream(xmlFile)).getDocumentElement();
    }

    public static Element readXmlToElement(final String xmlContent) throws SAXException, IOException {
        Document doc = readXmlToDocument(xmlContent);
        return doc.getDocumentElement();
    }

    public static Element readXmlToElement(final InputStream xmlContent) throws SAXException, IOException {
        Document doc = readXmlToDocument(xmlContent);
        return doc.getDocumentElement();
    }

    public static Document readXmlToDocument(final String xmlContent) throws SAXException, IOException {
        return readXmlToDocument(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }

    // TODO improve exceptions throwing
    // along with XmlElement

    public static Document readXmlToDocument(final InputStream xmlContent) throws SAXException, IOException {
        Document doc = DEFAULT_DOM_BUILDER.get().parse(xmlContent);

        doc.getDocumentElement().normalize();
        return doc;
    }

    public static Document newDocument() {
        return DEFAULT_DOM_BUILDER.get().newDocument();
    }

    /**
     * Return a new {@link Transformer} which performs indentation.
     *
     * @return A new Transformer
     * @throws TransformerConfigurationException if a Transformer can not be created
     */
    public static Transformer newIndentingTransformer() throws TransformerConfigurationException {
        final Transformer ret = PRETTY_PRINT_TEMPLATE.newTransformer();
        ret.setOutputProperty(OutputKeys.INDENT, "yes");
        return ret;
    }

    public static String toString(final Document document) {
        return toString(document.getDocumentElement());
    }

    public static String toString(final Element xml) {
        return toString(xml, false);
    }

    public static String toString(final XmlElement xmlElement) {
        return toString(xmlElement.getDomElement(), false);
    }

    public static String toString(final Element xml, final boolean addXmlDeclaration) {
        final StringWriter writer = new StringWriter();

        try {
            Transformer transformer = newIndentingTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, addXmlDeclaration ? "no" : "yes");
            transformer.transform(new DOMSource(xml), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IllegalStateException("Unable to serialize xml element " + xml, e);
        }

        return writer.toString();
    }

    public static String toString(final Document doc, final boolean addXmlDeclaration) {
        return toString(doc.getDocumentElement(), addXmlDeclaration);
    }

    public static Document createDocumentCopy(final Document original) {
        final Document copiedDocument = newDocument();
        final Node copiedRoot = copiedDocument.importNode(original.getDocumentElement(), true);
        copiedDocument.appendChild(copiedRoot);
        return copiedDocument;
    }
}
