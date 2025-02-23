/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3">Containment Node</a>.
 */
@NonNullByDefault
public final class ContainmentNode extends Sibling implements SiblingSet {
    private final List<ContentMatchNode> contentMatches;
    private final List<ContainmentNode> containments;
    private final List<SelectionNode> selections;

    ContainmentNode(final ContainmentNodeBuilder builder) {
        super(requireNonNull(builder.name()), builder.namespace());
        contentMatches = builder.siblings(ContentMatchNode.class);
        containments = builder.siblings(ContainmentNode.class);
        selections = builder.siblings(SelectionNode.class);
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
    int contributeHashCode() {
        return Objects.hash(contentMatches, containments, selections);
    }

    @Override
    boolean contributeEquals(final AbstractNamespaceSelection other) {
        final var cast = (ContainmentNode) other;
        return contentMatches.equals(cast.contentMatches) && containments.equals(cast.containments)
            && selections.equals(cast.selections);
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("siblings", siblings());
    }
}
