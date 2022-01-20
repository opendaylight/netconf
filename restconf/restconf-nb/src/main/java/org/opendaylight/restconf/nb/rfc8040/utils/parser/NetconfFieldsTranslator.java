/*
 * Copyright © 2020 FRINX s.r.o. and others.  All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * A translator between {@link FieldsParam} and {@link YangInstanceIdentifier}s suitable for use as field identifiers
 * in {@code netconf-dom-api}.
 *
 * <p>
 * Fields parser that stores set of {@link LinkedPathElement}s in each level. Using {@link LinkedPathElement} it is
 * possible to create a chain of path arguments and build complete paths since this element contains identifiers of
 * intermediary mixin nodes and also linked previous element.
 *
 * <p>
 * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:
 * <pre>
 * level 0: ['./a', './d']
 * level 1: ['a/b', '/d/x/e']
 * level 2: ['b/c']
 * </pre>
 */
public final class NetconfFieldsTranslator extends AbstractFieldsTranslator<NetconfFieldsTranslator.LinkedPathElement> {
    private static final NetconfFieldsTranslator INSTANCE = new NetconfFieldsTranslator();

    private NetconfFieldsTranslator() {
        // Hidden on purpose
    }

    /**
     * Translate a {@link FieldsParam} to a list of child node paths saved in lists, suitable for use with
     * {@link NetconfDataTreeService}.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of {@link YangInstanceIdentifier} that are relative to the last {@link PathArgument}
     *     of provided {@code identifier}
     */
    public static @NonNull List<YangInstanceIdentifier> translate(
            final @NonNull InstanceIdentifierContext identifier, final @NonNull FieldsParam input) {
        final List<Set<LinkedPathElement>> levels = INSTANCE.parseFields(identifier, input);
        final List<Map<PathArgument, LinkedPathElement>> mappedLevels = mapLevelsContentByIdentifiers(levels);
        return buildPaths(mappedLevels);
    }

    private static List<YangInstanceIdentifier> buildPaths(
            final List<Map<PathArgument, LinkedPathElement>> mappedLevels) {
        final List<YangInstanceIdentifier> completePaths = new ArrayList<>();
        // we must traverse levels from the deepest level to the top level, because each LinkedPathElement is only
        // linked to previous element
        for (int levelIndex = mappedLevels.size() - 1; levelIndex >= 0; levelIndex--) {
            // we go through unprocessed LinkedPathElements that represent leaves
            for (final LinkedPathElement pathElement : mappedLevels.get(levelIndex).values()) {
                if (pathElement.processed) {
                    // this element was already processed from the lower level - skip it
                    continue;
                }
                pathElement.processed = true;

                // adding deepest path arguments, LinkedList is used for more effective insertion at the 0 index
                final LinkedList<PathArgument> path = new LinkedList<>(pathElement.mixinNodesToTarget);
                path.add(pathElement.targetNodeIdentifier);

                PathArgument previousIdentifier = pathElement.previousNodeIdentifier;
                // adding path arguments from the linked LinkedPathElements recursively
                for (int buildingLevel = levelIndex - 1; buildingLevel >= 0; buildingLevel--) {
                    final LinkedPathElement previousElement = mappedLevels.get(buildingLevel).get(previousIdentifier);
                    path.addFirst(previousElement.targetNodeIdentifier);
                    path.addAll(0, previousElement.mixinNodesToTarget);
                    previousIdentifier = previousElement.previousNodeIdentifier;
                    previousElement.processed = true;
                }
                completePaths.add(YangInstanceIdentifier.create(path));
            }
        }
        return completePaths;
    }

    private static List<Map<PathArgument, LinkedPathElement>> mapLevelsContentByIdentifiers(
            final List<Set<LinkedPathElement>> levels) {
        // this step is used for saving some processing power - we can directly find LinkedPathElement using
        // representing PathArgument
        return levels.stream()
            .map(linkedPathElements -> linkedPathElements.stream()
                .map(linkedPathElement -> new SimpleEntry<>(linkedPathElement.targetNodeIdentifier, linkedPathElement))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)))
            .collect(Collectors.toList());
    }

    @Override
    protected DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode,
            final QName childQName, final Set<LinkedPathElement> level) {
        final List<PathArgument> collectedMixinNodes = new ArrayList<>();

        DataSchemaContextNode<?> actualContextNode = currentNode.getChild(childQName);
        while (actualContextNode != null && actualContextNode.isMixin()) {
            final var actualDataSchemaNode = actualContextNode.getDataSchemaNode();
            if (actualDataSchemaNode instanceof ListSchemaNode listSchema && listSchema.getKeyDefinition().isEmpty()) {
                // we need just a single node identifier from list in the path IFF it is an unkeyed list, otherwise
                // we need both (which is the default case)
                actualContextNode = actualContextNode.getChild(childQName);
            } else if (actualDataSchemaNode instanceof LeafListSchemaNode) {
                // NodeWithValue is unusable - stop parsing
                break;
            } else {
                collectedMixinNodes.add(actualContextNode.getIdentifier());
                actualContextNode = actualContextNode.getChild(childQName);
            }
        }

        if (actualContextNode == null) {
            throw new RestconfDocumentedException("Child " + childQName.getLocalName() + " node missing in "
                    + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final LinkedPathElement linkedPathElement = new LinkedPathElement(currentNode.getIdentifier(),
                collectedMixinNodes, actualContextNode.getIdentifier());
        level.add(linkedPathElement);
        return actualContextNode;
    }

    /**
     * {@link PathArgument} of data element grouped with identifiers of leading mixin nodes and previous node.<br>
     *  - identifiers of mixin nodes on the path to the target node - required for construction of full valid
     *    DOM paths,<br>
     *  - identifier of the previous non-mixin node - required to successfully create a chain of {@link PathArgument}s
     */
    static final class LinkedPathElement {
        private final PathArgument previousNodeIdentifier;
        private final List<PathArgument> mixinNodesToTarget;
        private final PathArgument targetNodeIdentifier;
        private boolean processed = false;

        /**
         * Creation of new {@link LinkedPathElement}.
         *
         * @param previousNodeIdentifier identifier of the previous non-mixin node
         * @param mixinNodesToTarget     identifiers of mixin nodes on the path to the target node
         * @param targetNodeIdentifier   identifier of target non-mixin node
         */
        private LinkedPathElement(final PathArgument previousNodeIdentifier,
                final List<PathArgument> mixinNodesToTarget, final PathArgument targetNodeIdentifier) {
            this.previousNodeIdentifier = previousNodeIdentifier;
            this.mixinNodesToTarget = mixinNodesToTarget;
            this.targetNodeIdentifier = targetNodeIdentifier;
        }

        @Override
        public boolean equals(final Object obj) {
            // this is need in order to make 'prepareQNameLevel(..)' working
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final LinkedPathElement that = (LinkedPathElement) obj;
            return targetNodeIdentifier.equals(that.targetNodeIdentifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetNodeIdentifier);
        }
    }
}