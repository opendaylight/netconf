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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3">Containment Node</a>.
 */
@NonNullByDefault
public final class ContainmentNode implements Sibling, SiblingSet {
    /**
     * A builder for {@link ContainmentNode}.
     */
    public static final class Builder extends SiblingSetBuilder {
        private final NamespaceSelection selection;

        private Builder(final NamespaceSelection selection) {
            this.selection = requireNonNull(selection);
        }

        public Builder add(final Sibling sibling) {
            addSibling(sibling);
            return this;
        }

        public ContainmentNode build() {
            return new ContainmentNode(this);
        }
    }

    private final NamespaceSelection selection;
    private final List<ContentMatchNode> contentMatches;
    private final List<ContainmentNode> containments;
    private final List<SelectionNode> selections;

    private ContainmentNode(final Builder builder) {
        selection = builder.selection;
        contentMatches = builder.siblings(ContentMatchNode.class);
        containments = builder.siblings(ContainmentNode.class);
        selections = builder.siblings(SelectionNode.class);
    }

    public static Builder builder(final NamespaceSelection selection) {
        return new Builder(selection);
    }

    @Override
    public NamespaceSelection selection() {
        return selection;
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
        return Objects.hash(selection, contentMatches, containments, selections);
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this || obj instanceof ContainmentNode other && selection.equals(other.selection)
            && contentMatches.equals(other.contentMatches) && containments.equals(other.containments)
            && selections.equals(other.selections);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("selection", selection).add("siblings", siblings()).toString();
    }
}
