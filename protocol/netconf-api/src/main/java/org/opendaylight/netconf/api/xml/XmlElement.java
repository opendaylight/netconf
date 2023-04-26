/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public final class XmlElement {
    public static final @NonNull String DEFAULT_NAMESPACE_PREFIX = "";

    private final Element element;

    private XmlElement(final Element element) {
        this.element = element;
    }

    public static XmlElement fromDomElement(final Element element) {
        return new XmlElement(element);
    }

    public static XmlElement fromDomDocument(final Document xml) {
        return new XmlElement(xml.getDocumentElement());
    }

    public static XmlElement fromString(final String str) throws DocumentedException {
        try {
            return new XmlElement(XmlUtil.readXmlToElement(str));
        } catch (IOException | SAXException e) {
            throw DocumentedException.wrap(e);
        }
    }

    public static XmlElement fromDomElementWithExpected(final Element element, final String expectedName)
            throws DocumentedException {
        XmlElement xmlElement = XmlElement.fromDomElement(element);
        xmlElement.checkName(expectedName);
        return xmlElement;
    }

    public static XmlElement fromDomElementWithExpected(final Element element, final String expectedName,
            final String expectedNamespace) throws DocumentedException {
        XmlElement xmlElement = XmlElement.fromDomElementWithExpected(element, expectedName);
        xmlElement.checkNamespace(expectedNamespace);
        return xmlElement;
    }

    public void checkName(final String expectedName) throws UnexpectedElementException {
        if (!getName().equals(expectedName)) {
            throw new UnexpectedElementException(
                    String.format("Expected %s xml element but was %s", expectedName, getName()),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public void checkNamespaceAttribute(final String expectedNamespace)
            throws UnexpectedNamespaceException, MissingNameSpaceException {
        if (!getNamespaceAttribute().equals(expectedNamespace)) {
            throw new UnexpectedNamespaceException(
                    String.format("Unexpected namespace %s should be %s", getNamespaceAttribute(), expectedNamespace),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public void checkNamespace(final String expectedNamespace)
            throws UnexpectedNamespaceException, MissingNameSpaceException {
        if (!getNamespace().equals(expectedNamespace)) {
            throw new UnexpectedNamespaceException(
                    String.format("Unexpected namespace %s should be %s", getNamespace(), expectedNamespace),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public String getName() {
        final var localName = element.getLocalName();
        return Strings.isNullOrEmpty(localName) ? element.getTagName() : localName;
    }

    public String getAttribute(final String attributeName) {
        return element.getAttribute(attributeName);
    }

    public String getAttribute(final String attributeName, final String namespace) {
        return element.getAttributeNS(namespace, attributeName);
    }

    public NodeList getElementsByTagName(final String name) {
        return element.getElementsByTagName(name);
    }

    public void appendChild(final Element toAppend) {
        element.appendChild(toAppend);
    }

    public Element getDomElement() {
        return element;
    }

    public Map<String, Attr> getAttributes() {
        final var attributes = element.getAttributes();
        final var result = new HashMap<String, Attr>();
        for (int i = 0, length = attributes.getLength(); i < length; i++) {
            if (attributes.item(i) instanceof Attr attr) {
                result.put(attr.getNodeName(), attr);
            }
        }
        return result;
    }

    /**
     * Non recursive.
     */
    private List<XmlElement> getChildElementsInternal(final Predicate<@NonNull Element> strat) {
        final var childNodes = element.getChildNodes();
        final var result = new ArrayList<XmlElement>();
        for (int i = 0, length = childNodes.getLength(); i < length; i++) {
            if (childNodes.item(i) instanceof Element elem && strat.test(elem)) {
                result.add(new XmlElement(elem));
            }
        }
        return result;
    }

    public List<XmlElement> getChildElements() {
        return getChildElementsInternal(e -> true);
    }

    /**
     * Returns the child elements for the given tag.
     *
     * @param tagName tag name without prefix
     * @return List of child elements
     */
    public List<XmlElement> getChildElements(final String tagName) {
        return getChildElementsInternal(e -> e.getLocalName().equals(tagName));
    }

    public List<XmlElement> getChildElementsWithinNamespace(final String childName, final String namespace) {
        return getChildElementsWithinNamespace(namespace).stream()
            .filter(xmlElement -> xmlElement.getName().equals(childName))
            .collect(Collectors.toList());
    }

    public List<XmlElement> getChildElementsWithinNamespace(final String namespace) {
        return getChildElementsInternal(e -> {
            final var elementNamespace = namespace(e);
            return elementNamespace != null && elementNamespace.equals(namespace);
        });
    }

    public Optional<XmlElement> getOnlyChildElementOptionally(final String childName) {
        final var children = getChildElements(childName);
        return children.size() != 1 ? Optional.empty() : Optional.of(children.get(0));
    }

    public Optional<XmlElement> getOnlyChildElementOptionally(final String childName, final String namespace) {
        final var children = getChildElementsWithinNamespace(namespace).stream()
            .filter(xmlElement -> xmlElement.getName().equals(childName))
            .collect(Collectors.toList());
        return children.size() != 1 ? Optional.empty() : Optional.of(children.get(0));
    }

    public Optional<XmlElement> getOnlyChildElementOptionally() {
        final var children = getChildElements();
        return children.size() != 1 ? Optional.empty() : Optional.of(children.get(0));
    }

    public XmlElement getOnlyChildElementWithSameNamespace(final String childName) throws  DocumentedException {
        return getOnlyChildElement(childName, getNamespace());
    }

    public XmlElement getOnlyChildElementWithSameNamespace() throws DocumentedException {
        XmlElement childElement = getOnlyChildElement();
        childElement.checkNamespace(getNamespace());
        return childElement;
    }

    public Optional<XmlElement> getOnlyChildElementWithSameNamespaceOptionally(final String childName) {
        final var namespace = namespace();
        if (namespace == null) {
            return Optional.empty();
        }

        final var children = getChildElementsWithinNamespace(namespace).stream()
            .filter(xmlElement -> xmlElement.getName().equals(childName))
            .collect(Collectors.toList());
        return children.size() != 1 ? Optional.empty() : Optional.of(children.get(0));
    }

    // FIXME: if we do not have a namespace this method always returns Optional.empty(). Why?!
    public Optional<XmlElement> getOnlyChildElementWithSameNamespaceOptionally() {
        final var optChild = getOnlyChildElementOptionally();
        if (optChild.isPresent()) {
            final var namespace = namespace();
            if (namespace != null && namespace.equals(optChild.orElseThrow().namespace())) {
                return optChild;
            }
        }
        return Optional.empty();
    }

    public XmlElement getOnlyChildElement(final String childName, final String namespace) throws DocumentedException {
        final var children = getChildElementsWithinNamespace(namespace).stream()
            .filter(xmlElement -> xmlElement.getName().equals(childName))
            .collect(Collectors.toList());
        if (children.size() != 1) {
            throw new DocumentedException(String.format("One element %s:%s expected in %s but was %s", namespace,
                    childName, toString(), children.size()),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }

        return children.get(0);
    }

    public XmlElement getOnlyChildElement(final String childName) throws DocumentedException {
        final var children = getChildElements(childName);
        if (children.size() != 1) {
            throw new DocumentedException("One element " + childName + " expected in " + toString(),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }
        return children.get(0);
    }

    public XmlElement getOnlyChildElement() throws DocumentedException {
        final var children = getChildElements();
        if (children.size() != 1) {
            throw new DocumentedException(
                    String.format("One element expected in %s but was %s", toString(), children.size()),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }
        return children.get(0);
    }

    public @NonNull String getTextContent() throws DocumentedException {
        final var children = element.getChildNodes();
        final var length = children.getLength();
        if (length == 0) {
            return DEFAULT_NAMESPACE_PREFIX;
        }
        for (int i = 0; i < length; i++) {
            if (children.item(i) instanceof Text textChild) {
                return textChild.getTextContent().trim();
            }
        }
        throw new DocumentedException(getName() + " should contain text",
            ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
    }

    public Optional<String> getOnlyTextContentOptionally() {
        // only return text content if this node has exactly one Text child node
        final var children = element.getChildNodes();
        if (children.getLength() == 1 && children.item(0) instanceof Text textChild) {
            return Optional.of(textChild.getWholeText());
        }
        return Optional.empty();
    }

    public @Nullable String namespaceAttribute() {
        final var attribute = element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE);
        return attribute.isEmpty() ? null : attribute;
    }

    public Optional<String> findNamespaceAttribute() {
        return Optional.ofNullable(namespaceAttribute());
    }

    public @NonNull String getNamespaceAttribute() throws MissingNameSpaceException {
        final var attribute = namespaceAttribute();
        if (attribute == null) {
            throw new MissingNameSpaceException("Element " + this + " must specify namespace",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
        return attribute;
    }

    public @Nullable String namespace() {
        return namespace(element);
    }

    private static @Nullable String namespace(final Element element) {
        final var namespaceURI = element.getNamespaceURI();
        return namespaceURI == null || namespaceURI.isEmpty() ? null : namespaceURI;
    }

    public Optional<String> findNamespace() {
        return Optional.ofNullable(namespace());
    }

    public @NonNull String getNamespace() throws MissingNameSpaceException {
        final var namespace = namespace();
        if (namespace == null) {
            throw new MissingNameSpaceException("No namespace defined for " + this,
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
        return namespace;
    }

    /**
     * Search for element's attributes defining namespaces. Look for the one
     * namespace that matches prefix of element's text content. E.g.
     *
     * <pre>
     * &lt;type
     * xmlns:th-java="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl"&gt;
     *     th-java:threadfactory-naming&lt;/type&gt;
     * </pre>
     *
     * <p>
     * returns {"th-java","urn:.."}. If no prefix is matched, then default
     * namespace is returned with empty string as key. If no default namespace
     * is found value will be null.
     */
    public Entry<String/* prefix */, String/* namespace */> findNamespaceOfTextContent()
            throws DocumentedException {
        final var textContent = getTextContent();
        final int firstColon = textContent.indexOf(':');
        final var textPrefix = firstColon == -1 ? DEFAULT_NAMESPACE_PREFIX : textContent.substring(0, firstColon);

        final var namespaces = extractNamespaces();
        if (!namespaces.containsKey(textPrefix)) {
            throw new IllegalArgumentException("Cannot find namespace for " + XmlUtil.toString(element)
                + ". Prefix from content is " + textPrefix + ". Found namespaces " + namespaces);
        }
        return new SimpleImmutableEntry<>(textPrefix, namespaces.get(textPrefix));
    }

    private Map<String, String> extractNamespaces() throws DocumentedException {
        final var namespaces = new HashMap<String, String>();
        final var attributes = element.getAttributes();
        for (int i = 0, length = attributes.getLength(); i < length; i++) {
            final var attribute = attributes.item(i);
            final var attribKey = attribute.getNodeName();
            if (attribKey.startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                final String prefix;
                if (attribKey.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                    prefix = DEFAULT_NAMESPACE_PREFIX;
                } else if (attribKey.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                    prefix = attribKey.substring(XMLConstants.XMLNS_ATTRIBUTE.length() + 1);
                } else {
                    throw new DocumentedException("Attribute does not start with :",
                        ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
                }
                namespaces.put(prefix, attribute.getNodeValue());
            }
        }

        // namespace does not have to be defined on this element but inherited
        if (!namespaces.containsKey(DEFAULT_NAMESPACE_PREFIX)) {
            final var namespace = namespace();
            if (namespace != null) {
                namespaces.put(DEFAULT_NAMESPACE_PREFIX, namespace);
            }
        }

        return namespaces;
    }

    public boolean hasNamespace() {
        return namespaceAttribute() != null || namespace() != null;
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof XmlElement other && element.isEqualNode(other.element);
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder("XmlElement{").append("name='").append(getName()).append('\'');
        final var namespace = namespace();
        if (namespace != null) {
            sb.append(", namespace='").append(namespace).append('\'');
        }
        return sb.append('}').toString();
    }
}
