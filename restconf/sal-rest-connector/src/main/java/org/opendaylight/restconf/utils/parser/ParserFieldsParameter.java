/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameter {
    private static final char COLON = ':';
    private static final char SEMICOLON = ';';
    private static final char SLASH = '/';
    private static final char STARTING_PARENTHESIS = '(';
    private static final char CLOSING_PARENTHESIS = ')';

    /**
     * Parse fields parameter and return complete list of child nodes organized into levels.
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List}
     */
    @Nonnull
    public static List<Set<QName>> parseFieldsParameter(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                        @Nonnull final String input) {
        final List<Set<QName>> parsed = new ArrayList<>();
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
     * @param input input value of fields parameter
     * @param startQNameModule starting qname module
     * @param startNode starting node
     * @param parsed list of results
     * @param context schema context
     */
    private static void parseInput(@Nonnull final String input,
                                   @Nonnull final QNameModule startQNameModule,
                                   @Nonnull final DataSchemaContextNode<?> startNode,
                                   @Nonnull final List<Set<QName>> parsed,
                                   @Nonnull final SchemaContext context) {
        int currentPosition = 0;
        int startPosition = 0;
        DataSchemaContextNode<?> currentNode = startNode;
        QNameModule currentQNameModule = startQNameModule;

        Set<QName> currentLevel = new HashSet<>();
        parsed.add(currentLevel);

        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);

            if (Deserializer.IDENTIFIER.matches(currentChar) || currentChar == '/') {
                if (currentChar == SLASH) {
                    // add parsed identifier to results for current level
                    currentNode = addChildToResult(
                            currentNode,
                            input.substring(startPosition, currentPosition), currentQNameModule, currentLevel);
                    // go one level down
                    currentLevel = new HashSet<>();
                    parsed.add(currentLevel);

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
                    currentQNameModule = context.findModuleByName(
                            input.substring(startPosition, currentPosition), null).getQNameModule();
                    currentPosition++;
                    break;
                case STARTING_PARENTHESIS:
                    // add current child to parsed results for current level
                    final DataSchemaContextNode<?> child = addChildToResult(
                            currentNode,
                            input.substring(startPosition, currentPosition), currentQNameModule, currentLevel);
                    // call with child node as new start node for one level down
                    int closingParenthesis = currentPosition
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
     * Add parsed child of current node to result for current level.
     * @param currentNode current node
     * @param identifier parsed identifier of child node
     * @param currentQNameModule current namespace and revision in {@link QNameModule}
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    @Nonnull
    private static DataSchemaContextNode<?> addChildToResult(
            @Nonnull final DataSchemaContextNode<?> currentNode,
            @Nonnull final String identifier,
            @Nonnull final QNameModule currentQNameModule,
            @Nonnull final Set<QName> level) {
        final QName childQName = QName.create(
                currentQNameModule.getNamespace().toString(),
                identifier,
                currentQNameModule.getRevision());

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
     * @param node initial mixin or not-mixin node
     * @param level current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    @Nullable
    private static DataSchemaContextNode<?> resolveMixinNode(@Nullable final DataSchemaContextNode<?> node,
                                                             @Nonnull final Set<QName> level,
                                                             @Nonnull final QName qualifiedName) {
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
    private static int findClosingParenthesis(@Nonnull final String input) {
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
