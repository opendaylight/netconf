/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.restconf.server.spi.EventFilter;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@NonNullByDefault
final class DataTreeCandidateSubtreeEventFilter extends EventFilter<List<DataTreeCandidate>> {
    private final SubtreeFilter filter;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    DataTreeCandidateSubtreeEventFilter(final SubtreeFilter filter) {
        super((Class) List.class);
        this.filter = requireNonNull(filter);
    }

    @Override
    protected boolean test(final EffectiveModelContext modelContext, final List<DataTreeCandidate> event) {
        return new SubtreeMatcher(filter, containerOfEvents(event), YangInstanceIdentifier.of()).matches();
    }

    private static ContainerNode containerOfEvents(final List<DataTreeCandidate> candidates) {
        final var firstArg = candidates.getFirst().getRootPath().getLastPathArgument();
        final var rootId = firstArg instanceof YangInstanceIdentifier.NodeIdentifier nip ? nip :
            YangInstanceIdentifier.NodeIdentifier.create(firstArg.getNodeType());
        final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(rootId);

        for (final DataTreeCandidate cand : candidates) {
            builder.withChild((ContainerNode) cand.getRootNode().dataAfter());
        }
        return builder.build();
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("filter", filter.prettyTree());
    }
}
