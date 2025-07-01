/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.netconf.databind.subtree.ContainmentNode;
import org.opendaylight.netconf.databind.subtree.SiblingSet;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

public final class SubtreeEventStreamFilter implements AbstractRestconfStreamRegistry.EventStreamFilter {
    private final SubtreeFilter filter;

    public SubtreeEventStreamFilter(final SubtreeFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(final YangInstanceIdentifier path, final ContainerNode body) {
        if (path.isEmpty()) {
            return new SubtreeMatcher(filter, body).matches();
        } else {
            return permitsPath(filter, path) && new SubtreeMatcher(filter, body, path).matches();
        }
    }

    /**
     * Return {@code true} when every QName which appears in {@code path} is accepted by this filter’s containment
     * hierarchy.
     *
     * <p>The method walks the path top-down and descends into exactly one containment node on each level;
     * it stops as soon as a segment has no matching containment.
     *
     * <p><b>Note:</b> This validates only the ancestor path of an instance notification (RFC7950). Content inside the
     * notification body is still evaluated later by {@link SubtreeMatcher}.
     */
    private static boolean permitsPath(final SubtreeFilter filter, final YangInstanceIdentifier path) {
        SiblingSet current = filter;
        for (final var arg : path.getPathArguments()) {
            ContainmentNode next = null;
            // look for a containment that accepts this QName
            for (final var cont : current.containments()) {
                if (cont.selection().matches(arg.getNodeType())) {
                    next = cont;
                    break;
                }
            }
            if (next == null) {
                // no match on this level
                return false;
            }
            // continue one level deeper
            current = next;
        }
        return true;
    }
}
