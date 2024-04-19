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
 * A baseline interface to filtering the output of {@link NormalizedNodeWriter}.
 */
@NonNullByDefault
abstract class NormalizedNodeFilter {
    /**
     * Check if node should be written according to parameters fields and depth.
     * See <a href="https://tools.ietf.org/html/draft-ietf-netconf-restconf-18#page-49">Restconf draft</a>.
     *
     * @param node Node to be written
     * @param mixinParent {@code true} if parent is mixin, {@code false} otherwise
     * @return {@code true} if node will be written, {@code false} otherwise
     */
    abstract boolean tryEnter(NormalizedNode node, boolean mixinParent);

    abstract void exit();

    abstract boolean isLastLevel();
}
