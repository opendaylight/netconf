/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.api.xml.MissingNameSpaceException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.concepts.PrettyTreeAware;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
        final XmlElement xml;
        try {
            xml = filter.getOnlyChildElement();
        } catch (DocumentedException e) {
            throw new IllegalArgumentException("Filter node has invalid structure", e);
        }
        final String namespace;
        try {
            namespace = xml.getNamespace();
        } catch (MissingNameSpaceException e) {
            throw new IllegalArgumentException("Element doesn't contain namespace", e);
        }
        final var domXml = xml.getDomElement();

        if (xml.getChildElements().isEmpty()) {
            // check if child nodes are xml or text to determine type of node
            final String content = getTextContent(xml);
            if (content.isEmpty()) {
                // return selection
                return builder().add(fillSelection(domXml)).build();
            } else {
                // return content
                return builder()
                    .add(new ContentMatchNode(new Exact(namespace, domXml.getNodeName()), domXml.getNodeValue()))
                    .build();
            }
        } else {
            // create containment
            final var containment = ContainmentNode.builder(new Exact(namespace, domXml.getNodeName()));

            // add attributes
            final var attributeMatches = collectAttributes(domXml);
            for(var attribute : attributeMatches) {
                containment.add(attribute);
            }

            // return containment
            return builder()
                .add(fillContainment(domXml, containment)
                    .build()).build();
        }
    }

    private static String getTextContent(final XmlElement xml) {
        final String content;
        try {
            content = xml.getTextContent();
        } catch (DocumentedException e) {
            throw new IllegalArgumentException("Content of node has invalid structure", e);
        }
        return content;
    }

    private static ContainmentNode.Builder fillContainment(final Element element,
            final ContainmentNode.Builder builder) {
        final var xml = XmlElement.fromDomElement(element);
        for (var child : xml.getChildElements()) {
            final var domChild = child.getDomElement();
            if (child.getChildElements().isEmpty()) {
                // check if child node has text content to determine type of node
                final String content = getTextContent(child);
                if (content.isEmpty()) {
                    // add selection
                    builder.add(fillSelection(child.getDomElement()));
                } else {
                    // add content
                    builder.add(new ContentMatchNode(new Exact(child.namespace(), domChild.getNodeName()), content))
                        .build();
                }
            } else {
                // add containment
                builder.add(fillContainment(domChild, ContainmentNode.builder(new Exact(child.namespace(),
                    domChild.getNodeName()))).build()).build();
            }
        }
        return builder;
    }

    private static SelectionNode fillSelection(final Element element) {
        final var xml = XmlElement.fromDomElement(element);
        // build selection
        final var selection = SelectionNode.builder(new Exact(xml.namespace(), element.getNodeName()));

        // add attributes
        final var attributeMatches = collectAttributes(element);
        for(var attribute : attributeMatches) {
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
                attributeMatches.add(new AttributeMatch(new Exact(attribute.getNamespaceURI(), attribute.getNodeName()),
                    attribute.getNodeValue()));
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
        writeSiblings(doc, xml, this);
    }

    private void writeSiblings(final Document doc, final XmlElement xml, final SiblingSet siblings) {
        for (final var contentMatch : siblings.contentMatches()) {
            final var child = writeSibling(contentMatch, doc);
            child.setNodeValue(contentMatch.value());
            xml.appendChild(child);
        }
        for (final var selection : siblings.selections()) {
            final var child = writeSibling(selection, doc);
            for (final var attr : selection.attributeMatches()) {
                child.setAttributeNS(attr.selection().namespace(), attr.selection().name(), attr.value());
            }
            xml.appendChild(child);
        }
        for (final var containment : siblings.containments()) {
            final var child = writeSibling(containment, doc);
            for (final var attr : containment.attributeMatches()) {
                child.setAttributeNS(attr.selection().namespace(), attr.selection().name(), attr.value());
            }
            writeSiblings(doc, XmlElement.fromDomElement(child), containment);
            xml.appendChild(child);
        }
    }

    private static Element writeSibling(Sibling containment, Document doc) {
        final Element child;
        switch (containment.selection()) {
            case Exact(var namespace, var name) -> {
                child = doc.createElementNS(namespace, name);
            }
            case NamespaceSelection.Wildcard(var name) -> {
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
