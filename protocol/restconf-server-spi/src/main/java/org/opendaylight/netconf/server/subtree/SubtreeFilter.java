/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.concepts.PrettyTreeAware;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6">Subtree Filter</a>.
 */
@NonNullByDefault
public final class SubtreeFilter implements Immutable, SiblingSet, PrettyTreeAware {
    private final List<ContentMatchNode> contentMatches;
    private final List<ContainmentNode> containments;
    private final List<SelectionNode> selections;
    final DatabindContext databind;

    SubtreeFilter(final SubtreeFilterBuilder builder) {
        databind = builder.databind;
        contentMatches = builder.siblings(ContentMatchNode.class);
        containments = builder.siblings(ContainmentNode.class);
        selections = builder.siblings(SelectionNode.class);
    }

    /**
     * Create a new {@link SubtreeFilterBuilder} bound to a {@link DatabindContext}.
     *
     * @param databind the {@link DatabindContext}
     * @return a new {@link SubtreeFilterBuilder}
     */
    public static SubtreeFilterBuilder builder(final DatabindContext databind) {
        return new SubtreeFilterBuilder(databind);
    }

    /**
     * Create a {@link SubtreeFilter} by reading the specification from a {@link XMLStreamReader} and interpreting it
     * in the context of a {@link DatabindContext}.
     *
     * @param databind the {@link DatabindContext}
     * @param reader the {@link XMLStreamReader}
     */
    public static SubtreeFilter readFrom(final DatabindContext databind, final XMLStreamReader reader) {
        return new SubtreeFilterStreamReader(databind, reader).readFilter();
    }

    /**
     * Write this filter's siblings into a {@link XMLStreamWriter}. For example, in produce an output like
     * {@snippet :
     * <filter type="subtree">
     *  <top xmlns="http://example.com/schema/1.2/config"/>
     * </filter>
     * }
     * the caller would perform
     * {@snippet :
     *   writer.writeStartElement("filter");
     *   writer.writeAttribute("type", "subtree");
     *   filter.writeTo(writer);
     *   writer.writeEndElement();
     * }
     *
     * @param writer the {@link XMLStreamWriter}
     */
    public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
        new SubtreeFilterStreamWriter(databind, writer).writeFilter(this);
    }

    /**
     * Match this filter against a {@link ContainerNode}.
     *
     * @param data the {@link ContainerNode}.
     * @return a {@link SubtreeMatcher}
     */
    public SubtreeMatcher matcher(final ContainerNode data) {
        return new SubtreeMatcher(this, data);
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
