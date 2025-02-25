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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.4">Selection Node</a>.
 */
@NonNullByDefault
public final class SelectionNode implements Sibling {
    public static final class Builder {
        private final ArrayList<AttributeMatch> attributeMatches = new ArrayList<>();
        private final NamespaceSelection selection;

        private Builder(final NamespaceSelection selection) {
            this.selection = requireNonNull(selection);
        }

        public Builder add(final AttributeMatch attributeMatch) {
            attributeMatches.add(requireNonNull(attributeMatch));
            return this;
        }

        public SelectionNode build() {
            return new SelectionNode(selection, List.copyOf(attributeMatches));
        }
    }

    private final List<AttributeMatch> attributeMatches;
    private final NamespaceSelection selection;

    private SelectionNode(final NamespaceSelection selection, final List<AttributeMatch> attributeMatches) {
        this.selection = requireNonNull(selection);
        this.attributeMatches = requireNonNull(attributeMatches);
    }

    public static Builder builder(final NamespaceSelection selection) {
        return new Builder(selection);
    }

    @Override
    public NamespaceSelection selection() {
        return selection;
    }

    public List<AttributeMatch> attributeMatches() {
        return attributeMatches;
    }

    @Override
    public int hashCode() {
        return Objects.hash(selection, attributeMatches);
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return this == obj || obj instanceof SelectionNode other && selection.equals(other.selection)
            && attributeMatches.equals(other.attributeMatches);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("selection", selection)
            .add("attributeMatches", attributeMatches)
            .toString();
    }
}
