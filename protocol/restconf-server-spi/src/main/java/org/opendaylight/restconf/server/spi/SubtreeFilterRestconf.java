/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See <a href="http://tools.ietf.org/html/rfc6241#section-6">rfc6241</a> for details.
 */
public final class SubtreeFilterRestconf {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilterRestconf.class);

    private SubtreeFilterRestconf() {
        // Hidden on purpose
    }

    public static NormalizedNode applyFilter(final NormalizedNode filterNode, final NormalizedNode dataNode) {
        if (filterNode == null || dataNode == null) {
            return null;
        }

        // Handle different node types
        if (filterNode instanceof ContainerNode filterContainer && dataNode instanceof ContainerNode dataContainer) {
            if ("stream-subtree-filter".equals(filterContainer.name().getNodeType().getLocalName())) {
                // Process children of stream-subtree-filter
                for (final var filterChild : filterContainer.body()) {
                    var matchingChild = dataContainer.findChildByArg(filterChild.name());
                    if (matchingChild.isPresent()) {
                        // Return the filtered child directly
                        return applyFilter(filterChild, matchingChild.orElseThrow());
                    }
                }
                return null; // No matching child found
            } else {
                return filterContainerNode(filterContainer, dataContainer);
            }
        } else if (filterNode instanceof MapNode filterMap && dataNode instanceof MapNode dataMap) {
            return filterMapNode(filterMap, dataMap);
        } else if (filterNode instanceof LeafNode filterLeaf && dataNode instanceof LeafNode dataLeaf) {
            return dataLeaf.name().equals(filterLeaf.name()) ? dataLeaf : null;
        }

        // If node types do not match or cannot be handled
        return null;
    }

    private static ContainerNode filterContainerNode(final ContainerNode filter, final ContainerNode data) {
        final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(filter.name());
        for (final var filterChild : filter.body()) {
            final var matchingChild = data.findChildByArg(filterChild.name());
            if (matchingChild.isPresent()) {
                var filteredChild = applyFilter(filterChild, matchingChild.orElseThrow());
                if (filteredChild != null) {
                    builder.withChild((DataContainerChild) filteredChild);
                }
            }
        }
        return builder.build();
    }

    private static MapNode filterMapNode(final MapNode filter, final MapNode data) {
        final var builder = ImmutableNodes.newUserMapBuilder().withNodeIdentifier(filter.name());
        for (var filterEntry : filter.body()) {
            final var matchingEntry = data.findChildByArg(filterEntry.name());
            if (matchingEntry.isPresent()) {
                final var filteredEntry = applyFilter(filterEntry, matchingEntry.orElseThrow());
                if (filteredEntry instanceof MapEntryNode mapEntryNode) {
                    builder.withChild(mapEntryNode);
                }
            }
        }
        return builder.build();
    }
}
