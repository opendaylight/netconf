/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class ParserFieldsParameter {
    private static final char COLON = ':';
    private static final char SEMICOLON = ';';
    private static final char SLASH = '/';
    private static final char STARTING_PARENTHESIS = '(';
    private static final char CLOSING_PARENTHESIS = ')';
    private static final List<String> PARENT_CHILD_RELATION_LIST = new ArrayList<String>();

    private ParserFieldsParameter() {

    }

    /**
     * Parse fields parameter and return complete list of child nodes organized into levels.
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List}
     */
    public static @NonNull List<Set<QName>> parseFieldsParameter(final @NonNull InstanceIdentifierContext<?> identifier,
                                                                 final @NonNull String input) {
        final List<Set<QName>> parsed = new ArrayList<>();
        final SchemaContext context = identifier.getSchemaContext();
        final QNameModule startQNameModule = identifier.getSchemaNode().getQName().getModule();
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        PARENT_CHILD_RELATION_LIST.clear();
        parseInput(input, startQNameModule, startNode, parsed, context);
        return parsed;
    }

    /**
     * Parse fields parameter and return parent child relation list.
     * @return {@link List}
     */
    public static List<String> getParentChildRelation() {
        return PARENT_CHILD_RELATION_LIST;
    }

    /**
     * Parse input value of fields parameter and create list of sets. Each set represents one level of child nodes.
     * @param input input value of fields parameter
     * @param startQNameModule starting qname module
     * @param startNode starting node
     * @param parsed list of results
     * @param context schema context
     */
    private static void parseInput(final @NonNull String input, final @NonNull QNameModule startQNameModule,
                                   final @NonNull DataSchemaContextNode<?> startNode,
                                   final @NonNull List<Set<QName>> parsed, final SchemaContext context) {
        int currentPosition = 0;
        int startPosition = 0;
        DataSchemaContextNode<?> currentNode = startNode;
        QNameModule currentQNameModule = startQNameModule;

        Set<QName> currentLevel = new HashSet<>();
        parsed.add(currentLevel);

        DataSchemaContextNode<?> parenthesisNode = currentNode;
        Set<QName> parenthesisLevel = currentLevel;
        QNameModule parenthesisQNameModule = currentQNameModule;

        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);

            if (Deserializer.IDENTIFIER.matches(currentChar) || currentChar == '/') {
                if (currentChar == SLASH) {
                    // add parsed identifier to results for current level
                    currentNode = addChildToResult(
                            currentNode,
                            input.substring(startPosition, currentPosition), currentQNameModule, currentLevel);
                    // go one level down
                    currentLevel = prepareQNameLevel(parsed, currentLevel);

                    currentPosition++;
                    startPosition = currentPosition;
                } else {
                    currentPosition++;
                }

                continue;
            }

            switch (currentChar) {
                case COLON :
                    // new namespace and revision found
                    currentQNameModule = context.findModules(
                            input.substring(startPosition, currentPosition)).iterator().next().getQNameModule();
                    currentPosition++;
                    break;
                case STARTING_PARENTHESIS:
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
                            if (input.charAt(currentPosition) == SEMICOLON) {
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
                case SEMICOLON:
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
     * Preparation of the QName level that is used as storage for parsed QNames. If the current level exist at the
     * index that doesn't equal to the last index of already parsed QNames, a new level of QNames is allocated and
     * pushed to input parsed QNames.
     *
     * @param parsedQNames Already parsed list of QNames grouped to multiple levels.
     * @param currentLevel Current level of QNames (set).
     * @return Existing or new level of QNames.
     */
    private static Set<QName> prepareQNameLevel(final List<Set<QName>> parsedQNames, final Set<QName> currentLevel) {
        final Optional<Set<QName>> existingLevel = parsedQNames.stream()
                .filter(qNameSet -> qNameSet.equals(currentLevel))
                .findAny();
        if (existingLevel.isPresent()) {
            final int index = parsedQNames.indexOf(existingLevel.get());
            if (index == parsedQNames.size() - 1) {
                final Set<QName> nextLevel = new HashSet<>();
                parsedQNames.add(nextLevel);
                return nextLevel;
            } else {
                return parsedQNames.get(index + 1);
            }
        } else {
            final Set<QName> nextLevel = new HashSet<>();
            parsedQNames.add(nextLevel);
            return nextLevel;
        }
    }

    /**
     * Add parsed child of current node to result for current level.
     * @param currentNode current node
     * @param identifier parsed identifier of child node
     * @param currentQNameModule current namespace and revision in {@link QNameModule}
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    private static @NonNull DataSchemaContextNode<?> addChildToResult(
            final @NonNull DataSchemaContextNode<?> currentNode, final @NonNull String identifier,
            final @NonNull QNameModule currentQNameModule, final @NonNull Set<QName> level) {
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
        PARENT_CHILD_RELATION_LIST.add(currentNode.getIdentifier().getNodeType().getLocalName()
            + "#" + childNode.getIdentifier().getNodeType().getLocalName());
        return childNode;
    }

    /**
     * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
     * All nodes expect of not mixin node are added to current level nodes.
     * @param node initial mixin or not-mixin node
     * @param level current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    private static @Nullable DataSchemaContextNode<?> resolveMixinNode(final @Nullable DataSchemaContextNode<?> node,
            final @NonNull Set<QName> level, final @NonNull QName qualifiedName) {
        DataSchemaContextNode<?> currentNode = node;
        while (currentNode != null && currentNode.isMixin()) {
            level.add(qualifiedName);
            currentNode = currentNode.getChild(qualifiedName);
        }

        return currentNode;
    }

    /**
     * Find position of matching parenthesis increased by one, but at most equals to input size.
     * @param input input where to find for closing parenthesis
     * @return int position of closing parenthesis increased by one
     */
    private static int findClosingParenthesis(final @Nullable String input) {
        int position = 0;
        int count = 1;

        while (position < input.length()) {
            final char currentChar = input.charAt(position);

            if (currentChar == STARTING_PARENTHESIS) {
                count++;
            }

            if (currentChar == CLOSING_PARENTHESIS) {
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
}
