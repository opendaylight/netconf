/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 *
 */
@NonNullByDefault
class DepthNormalizedNodeFilter extends NormalizedNodeFilter {
    private final @Nullable Integer maxDepth;

    private int currentDepth = 0;

    DepthNormalizedNodeFilter(final @Nullable Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    final boolean tryEnter(final NormalizedNode node, final boolean mixinParent) {
        if (canEnter(currentDepth, node, mixinParent)) {
            currentDepth++;
            return true;
        }
        return false;
    }

    boolean canEnter(final int depth, final NormalizedNode node, final boolean mixinParent) {
        final var local = maxDepth;
        return local == null || depth < local;
    }

    @Override
    final void exit() {
        currentDepth--;
    }

    @Override
    boolean isLastLevel() {
        final var local = maxDepth;
        return local != null && currentDepth == local;
    }
}
