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
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

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
                                                                 final @NonNull String input) {
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
            final @NonNull InstanceIdentifierContext<?> identifier, final @NonNull String input) {
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
                                              final @NonNull String input) {
        final List<Set<T>> parsed = new ArrayList<>();
        final SchemaContext context = identifier.getSchemaContext();
        final QNameModule startQNameModule = identifier.getSchemaNode().getQName().getModule();
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        parseInput(input, startQNameModule, startNode, parsed, context);
        return parsed;
    }

    /**
     * Parse input value of fields parameter and create list of sets. Each set represents one level of child nodes.
     *
     * @param input input value of fields parameter
     * @param startQNameModule starting qname module
     * @param startNode starting node
     * @param parsed list of results
     * @param context schema context
     */
    private void parseInput(final @NonNull String input, final @NonNull QNameModule startQNameModule,
                            final @NonNull DataSchemaContextNode<?> startNode,
                            final @NonNull List<Set<T>> parsed, final SchemaContext context) {
        int currentPosition = 0;
        int startPosition = 0;
        DataSchemaContextNode<?> currentNode = startNode;
        QNameModule currentQNameModule = startQNameModule;

        Set<T> currentLevel = new HashSet<>();
        parsed.add(currentLevel);

        DataSchemaContextNode<?> parenthesisNode = currentNode;
        Set<T> parenthesisLevel = currentLevel;
        QNameModule parenthesisQNameModule = currentQNameModule;

        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);

            if (ParserConstants.YANG_IDENTIFIER_PART.matches(currentChar)) {
                currentPosition++;
                continue;
            }

            switch (currentChar) {
                case '/':
                    // add parsed identifier to results for current level
                    currentNode = addChildToResult(currentNode, input.substring(startPosition, currentPosition),
                            currentQNameModule, currentLevel);
                    // go one level down
                    currentLevel = prepareQNameLevel(parsed, currentLevel);

                    currentPosition++;
                    break;
                case ':':
                    // new namespace and revision found
                    currentQNameModule = context.findModules(
                            input.substring(startPosition, currentPosition)).iterator().next().getQNameModule();
                    currentPosition++;
                    break;
                case '(':
                    // add current child to parsed results for current level
                    final DataSchemaContextNode<?> child = addChildToResult(
                            currentNode,
                            input.substring(startPosition, currentPosition), currentQNameModule, currentLevel);
                    // call with child node as new start node for one level down
                    final int closingParenthesis = currentPosition
                            + findClosingParenthesis(input.substring(currentPosition + 1));
                    parseInput(
                            input.substring(currentPosition + 1, closingParenthesis),
                            currentQNameModule,
                            child,
                            parsed,
                            context);

                    // closing parenthesis must be at the end of input or separator and one more character is expected
                    currentPosition = closingParenthesis + 1;
                    if (currentPosition != input.length()) {
                        if (currentPosition + 1 < input.length()) {
                            if (input.charAt(currentPosition) == ';') {
                                currentPosition++;
                            } else {
                                throw new RestconfDocumentedException(
                                        "Missing semicolon character after "
                                                + child.getIdentifier().getNodeType().getLocalName()
                                                + " child nodes",
                                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                            }
                        } else {
                            throw new RestconfDocumentedException(
                                    "Unexpected character '"
                                            + input.charAt(currentPosition)
                                            + "' found in fields parameter value",
                                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                        }
                    }

                    break;
                case ';':
                    // complete identifier found
                    addChildToResult(
                            currentNode,
                            input.substring(startPosition, currentPosition), currentQNameModule, currentLevel);
                    currentPosition++;

                    // next nodes can be placed on already utilized level-s
                    currentNode = parenthesisNode;
                    currentQNameModule = parenthesisQNameModule;
                    currentLevel = parenthesisLevel;
                    break;
                default:
                    throw new RestconfDocumentedException(
                            "Unexpected character '" + currentChar + "' found in fields parameter value",
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            startPosition = currentPosition;
        }

        // parse input to end
        if (startPosition < input.length()) {
            addChildToResult(currentNode, input.substring(startPosition), currentQNameModule, currentLevel);
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
     * Find position of matching parenthesis increased by one, but at most equals to input size.
     *
     * @param input input where to find for closing parenthesis
     * @return int position of closing parenthesis increased by one
     */
    private static int findClosingParenthesis(final @NonNull String input) {
        int position = 0;
        int count = 1;

        while (position < input.length()) {
            final char currentChar = input.charAt(position);

            if (currentChar == '(') {
                count++;
            }

            if (currentChar == ')') {
                count--;
            }

            if (count == 0) {
                break;
            }

            position++;
        }

        // closing parenthesis was not found
        if (position >= input.length()) {
            throw new RestconfDocumentedException("Missing closing parenthesis in fields parameter",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        return ++position;
    }

    /**
     * Add parsed child of current node to result for current level.
     *
     * @param currentNode current node
     * @param identifier parsed identifier of child node
     * @param currentQNameModule current namespace and revision in {@link QNameModule}
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    abstract @NonNull DataSchemaContextNode<?> addChildToResult(@NonNull DataSchemaContextNode<?> currentNode,
            @NonNull String identifier, @NonNull QNameModule currentQNameModule, @NonNull Set<T> level);

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
        DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode, final String identifier,
                                                  final QNameModule currentQNameModule, final Set<QName> level) {
            final QName childQName = QName.create(currentQNameModule, identifier);

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
                        "Child " + identifier + " node missing in "
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
        DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode, final String identifier,
                final QNameModule currentQNameModule, final Set<LinkedPathElement> level) {
            final QName childQName = QName.create(currentQNameModule, identifier);
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
                throw new RestconfDocumentedException("Child " + identifier + " node missing in "
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