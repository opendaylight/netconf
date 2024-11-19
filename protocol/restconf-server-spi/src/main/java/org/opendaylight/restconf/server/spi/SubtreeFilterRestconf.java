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

    /**
     * Applies a subtree filter to a given normalized data node. The filtering process
     * is based on the structure of the filter node, and the resulting node contains
     * only the elements matching the filter criteria.
     *
     * <p>This method supports filtering for container nodes, map nodes, and leaf nodes.
     * For container nodes, the filter recursively matches children nodes. For map nodes,
     * it matches entries based on their keys. Leaf nodes are matched based on their
     * names.</p>
     *
     * <p>See <a href="http://tools.ietf.org/html/rfc6241#section-6">RFC 6241, Section 6</a>
     * for details on subtree filtering in NETCONF.</p>
     *
     * @param filterNode the normalized node representing the filter. This node describes
     *                   the structure of the data to include in the filtered result.
     *                   Must be of type {@link NormalizedNode}.
     * @param dataNode the normalized node representing the data to be filtered.
     *                 Must be of type {@link NormalizedNode}.
     * @return the filtered {@link NormalizedNode}, or {@code null} if no match is found
     *         or if either the filterNode or dataNode is {@code null}.
     */
    public static NormalizedNode applyFilter(final NormalizedNode filterNode, final NormalizedNode dataNode) {
        if (filterNode == null || dataNode == null) {
            return null;
        }

        // Handle different node types
        switch (filterNode) {
            case ContainerNode filterContainer when dataNode instanceof ContainerNode dataContainer -> {
                if ("stream-subtree-filter".equals(filterContainer.name().getNodeType().getLocalName())) {
                    // Process children of stream-subtree-filter
                    for (final var filterChild : filterContainer.body()) {
                        var matchingChild = dataContainer.findChildByArg(filterChild.name());
                        if (matchingChild.isPresent()) {
                            // Return the filtered child directly
                            return applyFilter(filterChild, matchingChild.orElseThrow());
                        }
                    }
                    // No matching child found
                    return null;
                } else {
                    return filterContainerNode(filterContainer, dataContainer);
                }
            }
            case MapNode filterMap when dataNode instanceof MapNode dataMap -> {
                return filterMapNode(filterMap, dataMap);
            }
            case LeafNode filterLeaf when dataNode instanceof LeafNode dataLeaf -> {
                return dataLeaf.name().equals(filterLeaf.name()) ? dataLeaf : null;
            }
            default -> {
                LOG.warn("Unhandled filter node '{}' type '{}'.", filterNode.name(),
                    filterNode.getClass().getSimpleName());
                return null;
            }
        }
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
