/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam.NodeSelector;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Utilities used for parsing of fields query parameter content.
 *
 * @param <T> type of identifier
 */
public abstract class ParserFieldsParameter<T> {
    private static final ParserFieldsParameter<QName> QNAME_PARSER = new QNameParser();
    private static final ParserFieldsParameter<LinkedPathElement> PATH_PARSER = new PathParser();

    private ParserFieldsParameter() {
    }

    /**
     * Parse fields parameter and return complete list of child nodes organized into levels.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains set of {@link QName}
     */
    public static @NonNull List<Set<QName>> parseFieldsParameter(final @NonNull InstanceIdentifierContext<?> identifier,
                                                                 final @NonNull FieldsParam input) {
        return QNAME_PARSER.parseFields(identifier, input);
    }

    /**
     * Parse fields parameter and return list of child node paths saved in lists.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of {@link YangInstanceIdentifier} that are relative to the last {@link PathArgument}
     *     of provided {@code identifier}
     */
    public static @NonNull List<YangInstanceIdentifier> parseFieldsPaths(
            final @NonNull InstanceIdentifierContext<?> identifier, final @NonNull FieldsParam input) {
        final List<Set<LinkedPathElement>> levels = PATH_PARSER.parseFields(identifier, input);
        final List<Map<PathArgument, LinkedPathElement>> mappedLevels = mapLevelsContentByIdentifiers(levels);
        return buildPaths(mappedLevels);
    }

    private static List<Map<PathArgument, LinkedPathElement>> mapLevelsContentByIdentifiers(
            final List<Set<LinkedPathElement>> levels) {
        // this step is used for saving some processing power - we can directly find LinkedPathElement using
        // representing PathArgument
        return levels.stream()
                .map(linkedPathElements -> linkedPathElements.stream()
                        .map(linkedPathElement -> new SimpleEntry<>(linkedPathElement.targetNodeIdentifier,
                                linkedPathElement))
                        .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)))
                .collect(Collectors.toList());
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

    /**
     * Parse fields parameter and return complete list of child nodes organized into levels.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains {@link Set} of identifiers of type {@link T}
     */
    private @NonNull List<Set<T>> parseFields(final @NonNull InstanceIdentifierContext<?> identifier,
                                              final @NonNull FieldsParam input) {
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final List<Set<T>> parsed = new ArrayList<>();
        processSelectors(parsed, identifier.getSchemaContext(), identifier.getSchemaNode().getQName().getModule(),
            startNode, input.nodeSelectors());
        return parsed;
    }

    private void processSelectors(final List<Set<T>> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final DataSchemaContextNode<?> startNode,
            final List<NodeSelector> selectors) {
        final Set<T> startLevel = new HashSet<>();
        parsed.add(startLevel);

        for (var selector : selectors) {
            var node = startNode;
            var namespace = startNamespace;
            var level = startLevel;


            // Note: path is guaranteed to have at least one step
            final var it = selector.path().iterator();
            while (true) {
                // FIXME: The layout of this loop is rather weird, which is due to how prepareQNameLevel() operates. We
                //        need to call it only when we know there is another identifier coming, otherwise we would end
                //        up with empty levels sneaking into the mix.
                //
                //        Dealing with that weirdness requires understanding what the expected end results are and a
                //        larger rewrite of the algorithms involved.
                final var step = it.next();
                final var module = step.module();
                if (module != null) {
                    // FIXME: this is not defensive enough, as we can fail to find the module
                    namespace = context.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed identifier to results for current level
                node = addChildToResult(node, step.identifier().bindTo(namespace), level);
                if (!it.hasNext()) {
                    break;
                }

                // go one level down
                level = prepareQNameLevel(parsed, level);
            }

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, context, namespace, node, subs);
            }
        }
    }

    /**
     * Preparation of the identifiers level that is used as storage for parsed identifiers. If the current level exist
     * at the index that doesn't equal to the last index of already parsed identifiers, a new level of identifiers
     * is allocated and pushed to input parsed identifiers.
     *
     * @param parsedIdentifiers Already parsed list of identifiers grouped to multiple levels.
     * @param currentLevel Current level of identifiers (set).
     * @return Existing or new level of identifiers.
     */
    private Set<T> prepareQNameLevel(final List<Set<T>> parsedIdentifiers, final Set<T> currentLevel) {
        final Optional<Set<T>> existingLevel = parsedIdentifiers.stream()
                .filter(qNameSet -> qNameSet.equals(currentLevel))
                .findAny();
        if (existingLevel.isPresent()) {
            final int index = parsedIdentifiers.indexOf(existingLevel.get());
            if (index == parsedIdentifiers.size() - 1) {
                final Set<T> nextLevel = new HashSet<>();
                parsedIdentifiers.add(nextLevel);
                return nextLevel;
            }

            return parsedIdentifiers.get(index + 1);
        }

        final Set<T> nextLevel = new HashSet<>();
        parsedIdentifiers.add(nextLevel);
        return nextLevel;
    }

    /**
     * Add parsed child of current node to result for current level.
     *
     * @param currentNode current node
     * @param childQName parsed identifier of child node
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    abstract @NonNull DataSchemaContextNode<?> addChildToResult(@NonNull DataSchemaContextNode<?> currentNode,
            @NonNull QName childQName, @NonNull Set<T> level);

    /**
     * Fields parser that stores set of {@link QName}s in each level. Because of this fact, from the output
     * it is is only possible to assume on what depth the selected element is placed. Identifiers of intermediary
     * mixin nodes are also flatten to the same level as identifiers of data nodes.<br>
     * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
     * <pre>
     * level 0: ['a', 'd']
     * level 1: ['b', 'x', 'e']
     * level 2: ['c']
     * </pre>
     */
    private static final class QNameParser extends ParserFieldsParameter<QName> {
        @Override
        DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode, final QName childQName,
                                                  final Set<QName> level) {
            // resolve parent node
            final DataSchemaContextNode<?> parentNode = resolveMixinNode(
                    currentNode, level, currentNode.getIdentifier().getNodeType());
            if (parentNode == null) {
                throw new RestconfDocumentedException(
                        "Not-mixin node missing in " + currentNode.getIdentifier().getNodeType().getLocalName(),
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            // resolve child node
            final DataSchemaContextNode<?> childNode = resolveMixinNode(
                    parentNode.getChild(childQName), level, childQName);
            if (childNode == null) {
                throw new RestconfDocumentedException(
                        "Child " + childQName.getLocalName() + " node missing in "
                                + currentNode.getIdentifier().getNodeType().getLocalName(),
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            // add final childNode node to level nodes
            level.add(childNode.getIdentifier().getNodeType());
            return childNode;
        }

        /**
         * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
         * All nodes expect of not mixin node are added to current level nodes.
         *
         * @param node          initial mixin or not-mixin node
         * @param level         current nodes level
         * @param qualifiedName qname of initial node
         * @return {@link DataSchemaContextNode}
         */
        private static @Nullable DataSchemaContextNode<?> resolveMixinNode(
                final @Nullable DataSchemaContextNode<?> node, final @NonNull Set<QName> level,
                final @NonNull QName qualifiedName) {
            DataSchemaContextNode<?> currentNode = node;
            while (currentNode != null && currentNode.isMixin()) {
                level.add(qualifiedName);
                currentNode = currentNode.getChild(qualifiedName);
            }

            return currentNode;
        }
    }

    /**
     * Fields parser that stores set of {@link LinkedPathElement}s in each level. Using {@link LinkedPathElement}
     * it is possible to create a chain of path arguments and build complete paths since this element contains
     * identifiers of intermediary mixin nodes and also linked previous element.<br>
     * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
     * <pre>
     * level 0: ['./a', './d']
     * level 1: ['a/b', '/d/x/e']
     * level 2: ['b/c']
     * </pre>
     */
    private static final class PathParser extends ParserFieldsParameter<LinkedPathElement> {
        @Override
        DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode, final QName childQName,
                                                  final Set<LinkedPathElement> level) {
            final List<PathArgument> collectedMixinNodes = new ArrayList<>();

            DataSchemaContextNode<?> actualContextNode = currentNode.getChild(childQName);
            while (actualContextNode != null && actualContextNode.isMixin()) {
                if (actualContextNode.getDataSchemaNode() instanceof ListSchemaNode) {
                    // we need just a single node identifier from list in the path (key is not available)
                    actualContextNode = actualContextNode.getChild(childQName);
                    break;
                } else if (actualContextNode.getDataSchemaNode() instanceof LeafListSchemaNode) {
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
    }

    /**
     * {@link PathArgument} of data element grouped with identifiers of leading mixin nodes and previous node.<br>
     *  - identifiers of mixin nodes on the path to the target node - required for construction of full valid
     *    DOM paths,<br>
     *  - identifier of the previous non-mixin node - required to successfully create a chain of {@link PathArgument}s
     */
    private static final class LinkedPathElement {
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