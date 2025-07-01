/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.concepts.PrettyTreeAware;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6">Subtree Filter</a>.
 */
@NonNullByDefault
public final class SubtreeFilter implements Immutable, PrettyTreeAware, SiblingSet {
    /**
     * A builder for {@link SubtreeFilter}.
     */
    public static final class Builder extends SiblingSetBuilder {
        private final DatabindContext databind;

        private Builder(final DatabindContext databind) {
            this.databind = requireNonNull(databind);
        }

        public Builder add(final Sibling sibling) {
            addSibling(sibling);
            return this;
        }

        public SubtreeFilter build() {
            return new SubtreeFilter(this);
        }
    }

    private final DatabindContext databind;
    private final List<ContentMatchNode> contentMatches;
    private final List<ContainmentNode> containments;
    private final List<SelectionNode> selections;

    private SubtreeFilter(final Builder builder) {
        databind = builder.databind;
        contentMatches = builder.siblings(ContentMatchNode.class);
        containments = builder.siblings(ContainmentNode.class);
        selections = builder.siblings(SelectionNode.class);
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @param databind the {@link DatabindContext}
     * @return a new builder
     */
    public static SubtreeFilter.Builder builder(final DatabindContext databind) {
        return new Builder(databind);
    }

    /**
     * Create a {@link SubtreeFilter} by reading the specification from an {@link XMLStreamReader} and interpreting it
     * in the context of a particular {@link DatabindContext}.
     *
     * @param databind the {@link DatabindContext}
     * @param reader the {@link XMLStreamReader}
     * @return a filter
     * @throws XMLStreamException when an error occurs
     */
    public static SubtreeFilter readFrom(final DatabindContext databind, final XMLStreamReader reader)
            throws XMLStreamException {
        return SubtreeFilterReader.readSubtreeFilter(reader, databind);
    }

    @Override
    public PrettyTree prettyTree() {
        return new SubtreeFilterPrettyTree(this);
    }

    /**
     * Write this filter's siblings into an {@link XMLStreamWriter}.
     *
     * @param writer the writer
     * @throws XMLStreamException if an error occurs
     */
    public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
        SubtreeFilterWriter.writeSubtreeFilter(writer, this);
    }

    public DatabindContext databind() {
        return databind;
    }

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
