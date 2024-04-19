/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 *
 */
@NonNullByDefault
final class FieldsNormalizedNodeFilter extends DepthNormalizedNodeFilter {
    private final List<Set<QName>> fields;

    FieldsNormalizedNodeFilter(final List<Set<QName>> fields, final @Nullable Integer maxDepth) {
        super(maxDepth);
        this.fields = requireNonNull(fields);
    }

    @Override
    boolean canEnter(final int depth, final NormalizedNode node, final boolean mixinParent) {
        // children of mixin nodes are never selected in fields but must be written if they are first in selected target
        if (mixinParent && depth == 0) {
            return true;
        }

        // write only selected nodes
        if (depth > 0 && depth <= fields.size()) {
            return fields.get(depth - 1).contains(node.name().getNodeType());
        }

        // after this depth only depth parameter is used to determine when to write node
        return super.canEnter(depth, node, mixinParent);
    }

    @Override
    boolean isLastLevel() {
        return false;
    }
}
