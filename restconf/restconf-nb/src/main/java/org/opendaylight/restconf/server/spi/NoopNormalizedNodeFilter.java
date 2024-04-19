/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A pass-through {@link NormalizedNodeFilter}.
 */
@NonNullByDefault
final class NoopNormalizedNodeFilter extends NormalizedNodeFilter {
    static final NoopNormalizedNodeFilter INSTANCE = new NoopNormalizedNodeFilter();

    private NoopNormalizedNodeFilter() {
        // Hidden on purpose
    }

    @Override
    boolean tryEnter(final NormalizedNode node, final boolean mixinParent) {
        return true;
    }

    @Override
    void exit() {
        // No-op
   }

    @Override
    boolean isLastLevel() {
        throw new UnsupportedOperationException();
    }
}
