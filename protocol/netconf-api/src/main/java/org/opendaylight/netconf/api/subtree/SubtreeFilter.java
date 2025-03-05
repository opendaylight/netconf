/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.api.subtree.NamespaceSelection.Wildcard;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.concepts.PrettyTreeAware;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6">Subtree Filter</a>.
 */
@NonNullByDefault
public final class SubtreeFilter implements Immutable, SiblingSet, PrettyTreeAware {
    /**
     * A builder for {@link SubtreeFilter}.
     */
    public static final class Builder extends SiblingSetBuilder {
        private Builder() {
            // hidden on purpose
        }

        public Builder add(final Sibling sibling) {
            addSibling(sibling);
            return this;
        }

        public SubtreeFilter build() {
            return new SubtreeFilter(this);
        }
    }

    private final List<ContentMatchNode> contentMatches;
    private final List<ContainmentNode> containments;
    private final List<SelectionNode> selections;

    private SubtreeFilter(final Builder builder) {
        contentMatches = builder.siblings(ContentMatchNode.class);
        containments = builder.siblings(ContainmentNode.class);
        selections = builder.siblings(SelectionNode.class);
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a new builder
     */
    public static SubtreeFilter.Builder builder() {
        return new Builder();
    }

    /**
     * Create a {@link SubtreeFilter} by reading the specification from an {@link Element}.
     *
     * @param element the element
     * @return a filter
     * @throws IllegalArgumentException if the proposed element has invalid structure
     */
    public static SubtreeFilter readFrom(final Element element) {
        final var filter = XmlElement.fromDomElement(element);
        final var subtreeFilterBuilder = new Builder();
        for (final var xml : filter.getChildElements()) {
            final var domXml = xml.getDomElement();

            // read child nodes of <filter>
            if (xml.getChildElements().isEmpty()) {
                // check if node has text to determine type of node
                final var content = domXml.getTextContent();
                if (content.isEmpty()) {
                    // add selection
                    subtreeFilterBuilder.add(fillSelection(domXml));
                } else {
                    // add content
                    subtreeFilterBuilder.add(new ContentMatchNode(getNamespaceSelection(domXml), content));
                }
            } else {
                // create containment
                final var containment = ContainmentNode.builder(getNamespaceSelection(domXml));

                // add containment
                subtreeFilterBuilder.add(fillContainment(domXml, containment).build());
            }
        }
        return subtreeFilterBuilder.build();
    }

    private static NamespaceSelection getNamespaceSelection(final Node node) {
        if (node.getNamespaceURI() != null) {
            return new Exact(node.getNamespaceURI(), node.getLocalName());
        } else {
            return new Wildcard(node.getLocalName());
        }
    }

    private static ContainmentNode.Builder fillContainment(final Element element,
            final ContainmentNode.Builder builder) {
        final var xml = XmlElement.fromDomElement(element);
        for (var child : xml.getChildElements()) {
            final var domChild = child.getDomElement();
            if (child.getChildElements().isEmpty()) {
                // check if child node has text content to determine type of node
                final String content = domChild.getTextContent();
                if (content.isEmpty()) {
                    // add selection
                    builder.add(fillSelection(child.getDomElement()));
                } else {
                    // add content
                    builder.add(new ContentMatchNode(getNamespaceSelection(domChild), content))
                        .build();
                }
            } else {
                // add containment
                builder.add(fillContainment(domChild, ContainmentNode.builder(getNamespaceSelection(domChild))).build())
                    .build();
            }
        }
        return builder;
    }

    private static SelectionNode fillSelection(final Element element) {
        // build selection
        final var selection = SelectionNode.builder(getNamespaceSelection(element));

        // add attributes
        final var attributeMatches = collectAttributes(element);
        for (var attribute : attributeMatches) {
            selection.add(attribute);
        }

        // return filled selection
        return selection.build();
    }

    private static List<AttributeMatch> collectAttributes(final Element element) {
        final var attributeMatches = new ArrayList<AttributeMatch>();
        if (element.hasAttributes()) {
            final var attributes = element.getAttributes();
            for (var i = 0; i < attributes.getLength(); i++) {
                final var attribute = attributes.item(i);
                // skip namespace
                // check by "namespace for namespaces" to include only them
                final var attrNamespace = attribute.getNamespaceURI();
                if (XMLNS_ATTRIBUTE_NS_URI.equals(attrNamespace)) {
                    continue;
                } else if (attrNamespace == null || attrNamespace.isEmpty()) {
                    throw new IllegalArgumentException("Attribute has no namespace");
                }
                // add attribute
                attributeMatches.add(new AttributeMatch(new Exact(attribute.getNamespaceURI(),
                    attribute.getLocalName()), attribute.getNodeValue()));
            }
        }
        return attributeMatches;
    }

    /**
     * Write this filter's siblings into a {@link Element}.
     *
     * @param element the element
     * @throws IllegalArgumentException if the proposed element has invalid structure
     */
    public void writeTo(final Element element) {
        final var doc = element.getOwnerDocument();
        final var xml = XmlElement.fromDomElement(element);

        // generate prefixes for namespaces
        final var prefixes = Prefixes.of(this);

        writeSiblings(doc, xml, prefixes, this);
    }

    private void writeSiblings(final Document doc, final XmlElement xml, final Prefixes prefixes,
            final SiblingSet siblings) {
        for (final var contentMatch : siblings.contentMatches()) {
            final var child = writeSibling(contentMatch, doc, prefixes);
            child.setTextContent(contentMatch.value());
            xml.appendChild(child);
        }
        for (final var selection : siblings.selections()) {
            final var child = writeSibling(selection, doc, prefixes);
            for (final var attr : selection.attributeMatches()) {
                child.setAttributeNS(attr.selection().namespace(), attr.selection().name(), attr.value());
            }
            xml.appendChild(child);
        }
        for (final var containment : siblings.containments()) {
            final var child = writeSibling(containment, doc, prefixes);
            writeSiblings(doc, XmlElement.fromDomElement(child), prefixes, containment);
            xml.appendChild(child);
        }
    }

    private static Element writeSibling(final Sibling sibling, final Document doc, final Prefixes prefixes) {
        final Element child;
        switch (sibling.selection()) {
            case Exact(var namespace, var name) -> {
                child = doc.createElementNS(namespace, name);
                child.setPrefix(prefixes.getPrefix(namespace));
            }
            case Wildcard(var name) -> {
                child = doc.createElement(name);
            }
        }
        return child;
    }

    @Override
    public List<ContentMatchNode> contentMatches() {
        return contentMatches;
    }

    @Override
    public List<ContainmentNode> containments() {
        return containments;
    }

    @Override
    public List<SelectionNode> selections() {
        return selections;
    }

    @Override
    public PrettyTree prettyTree() {
        return new SubtreeFilterPrettyTree(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentMatches, containments, selections);
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return this == obj || obj instanceof SubtreeFilter other && contentMatches.equals(other.contentMatches)
            && containments.equals(other.containments) && selections.equals(other.selections);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("siblings", siblings()).toString();
    }
}
