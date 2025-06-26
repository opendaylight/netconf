/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.opendaylight.netconf.databind.subtree.SubtreeMatcher.permitsPath;

import com.google.common.base.MoreObjects;
import java.util.List;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class SubtreeEventStreamFilter<T> extends EventFilter<T>
        implements AbstractRestconfStreamRegistry.EventStreamFilter {
    private final SubtreeFilter filter;

    public SubtreeEventStreamFilter(final SubtreeFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(final YangInstanceIdentifier path, final ContainerNode body) {
        return permitsPath(filter, path) && new SubtreeMatcher(filter, body, path).matches();
    }

    @Override
    boolean matches(final EffectiveModelContext modelContext, final T event) {
        final var path = extractPath(event);
        final var body  = extractBody(event);
        return path != null && body != null && test(path, body);
    }

    private static <T> YangInstanceIdentifier extractPath(final T event) {
        return switch (event) {
            case DOMNotificationEvent.Rfc7950 n7950 -> n7950.path();
            case DOMNotificationEvent.Rfc6020 ignored -> YangInstanceIdentifier.of();
            case DOMNotification ignored -> YangInstanceIdentifier.of();
            case List<?> list when !list.isEmpty() && list.getFirst() instanceof DataTreeCandidate ->
                YangInstanceIdentifier.of();
            default -> null;
        };
    }


    @SuppressWarnings("unchecked")
    private static <T> ContainerNode extractBody(T event) {
        return switch (event) {
            case DOMNotificationEvent.Rfc7950 n7950 -> n7950.getBody();
            case DOMNotificationEvent.Rfc6020 n6020 -> n6020.getBody();
            case DOMNotification notification -> notification.getBody();
            case List<?> list when !list.isEmpty() && list.getFirst() instanceof DataTreeCandidate candidates ->
                parseDataTreeCandidates((List<DataTreeCandidate>) candidates);
            default -> null;
        };
    }

    private static ContainerNode parseDataTreeCandidates(final List<DataTreeCandidate> candidates) {
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
    protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper helper) {
        return helper;
    }
}
