/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class SubtreeEventStreamFilter implements AbstractRestconfStreamRegistry.EventStreamFilter {
    private final SubtreeFilter filter;

    public SubtreeEventStreamFilter(final SubtreeFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(final YangInstanceIdentifier path, final ContainerNode body) {
        return matches(null, new AbstractRestconfStreamRegistry.FilterEvent(path, body));
    }

    @Override
    public boolean matches(final @Nullable EffectiveModelContext ctx, final AbstractRestconfStreamRegistry.FilterEvent event) {
        if (!event.path().isEmpty() && filter.permitsQName(event.path().getLastPathArgument().getNodeType())) {
            return false;
        }
        return new SubtreeMatcher(filter, event.body()).matches();
    }

}
