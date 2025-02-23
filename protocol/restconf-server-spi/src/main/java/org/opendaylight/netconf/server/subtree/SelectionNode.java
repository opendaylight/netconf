/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.4">Selection Node</a>.
 */
@NonNullByDefault
public final class SelectionNode extends Sibling {

    public static final class Builder {
        Builder() {
            // Hidden on purpose
        }

        public SelectionNode build() {

            return null;
        }
    }

    private final List<AttributeMatch> attributeMatches;

    private SelectionNode(final String localName, final @Nullable XMLNamespace namespace,
            final List<AttributeMatch> attributeMatches) {
        super(localName, namespace);
        this.attributeMatches = requireNonNull(attributeMatches);
    }

    public List<AttributeMatch> attributeMatches() {
        return attributeMatches;
    }

    @Override
    int contributeHashCode() {
        return attributeMatches.hashCode();
    }

    @Override
    boolean contributeEquals(final AbstractNamespaceSelection other) {
        return attributeMatches.equals(((SelectionNode) other).attributeMatches);
    }
}
