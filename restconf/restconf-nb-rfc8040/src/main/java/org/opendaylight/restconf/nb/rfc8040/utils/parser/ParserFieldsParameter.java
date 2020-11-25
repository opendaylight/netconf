/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class ParserFieldsParameter {
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
        parseInput(input, startQNameModule, startNode, parsed, context);
        return parsed;
    }

    /**
     * Parse fields parameter and return list of child node paths saved in lists.
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List}
     */
    public static @NonNull List<YangInstanceIdentifier> parseFieldsPaths(
            final @NonNull InstanceIdentifierContext<?> identifier, final @NonNull String input) {
        List<List<PathArgument>> parsed2 = new ArrayList<>();
        parsed2.add(new ArrayList<>());
        final SchemaContext context = identifier.getSchemaContext();
        final QNameModule startQNameModule = identifier.getSchemaNode().getQName().getModule();
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        return parseInputForMountPoint(input, startQNameModule, startNode, parsed2, context).stream()
                .map(pathArguments -> Iterables.skip(pathArguments, 1))
                .map(YangInstanceIdentifier::create)
                .collect(Collectors.toList());
    }

    /**
     * Parse input value of fields parameter and create list of lists. Each sub-list represents one path child node.
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
            }

            return parsedQNames.get(index + 1);
        }

        final Set<QName> nextLevel = new HashSet<>();
        parsedQNames.add(nextLevel);
        return nextLevel;
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
                                                                       final @NonNull Set<QName> level,
                                                                       final @NonNull QName qualifiedName) {
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
     * Add parsed child of current node to the currently parsed path.
     * @param path node path
     * @param childQname child to be found, checked and added
     * @param currentNode current node
     * @return {@link DataSchemaContextNode}
     */
    private static DataSchemaContextNode<?> resolvePath(final @NonNull List<PathArgument> path,
                                                                 @NonNull QName childQname,
                                                                 final @NonNull DataSchemaContextNode<?> currentNode) {
        // resolve parent node
        DataSchemaContextNode<?> parentNode = resolveMountPointMixinNode(currentNode, path, currentNode.getIdentifier()
                .getNodeType());
        if (parentNode == null) {
            throw new RestconfDocumentedException(
                    "Not-mixin node missing in " + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        // add potential choice node
        if (parentNode.getChild(childQname) != null
                && !path.contains(parentNode.getChild(childQname).getIdentifier())) {
            path.add(parentNode.getChild(childQname).getIdentifier());
        }
        // resolve child node
        DataSchemaContextNode<?> childNode = resolveMountPointMixinNode(
                parentNode.getChild(childQname), path, childQname);
        if (childQname == null) {
            throw new RestconfDocumentedException(
                    "Child " + childQname.getLocalName() + " node missing in "
                            + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        //if (!path.contains(parentNode.getIdentifier())) {
        //    path.add(0, parentNode.getIdentifier());
        //}
        // add final childNode node to level nodes
        if (childNode != null && !childNode.isLeaf() && !path.contains(childNode.getIdentifier())) {
            path.add(childNode.getIdentifier());
        }
        return childNode;
    }

    /**
     * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
     * All node identifiers expect of not mixin node are added to currently checked node path.
     * @param node initial mixin or not-mixin node
     * @param path current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    private static @Nullable DataSchemaContextNode<?> resolveMountPointMixinNode(
            final @NonNull DataSchemaContextNode<?> node, final @NonNull List<PathArgument> path,
            final @NonNull QName qualifiedName) {
        DataSchemaContextNode<?> currentNode = node;
        while (currentNode != null && currentNode.isMixin()) {
            if (!path.contains(YangInstanceIdentifier.of(qualifiedName).getLastPathArgument())) {
                path.add(YangInstanceIdentifier.of(qualifiedName).getLastPathArgument());
            }
            currentNode = currentNode.getChild(qualifiedName);
        }

        return currentNode;
    }

    /**
     * Finds name of the next node from fields query.
     * @param fields to be searched for next node name
     * @return local name of next node
    */
    private static @NonNull String findNextNodeName(String fields) {
        int charIndex = 0;
        StringBuilder nodeName = new StringBuilder();
        while (charIndex < fields.length()) {
            if ("/();".contains(Character.toString(fields.charAt(charIndex)))) {
                return nodeName.toString();
            }
            nodeName.append(fields.charAt(charIndex));
            charIndex++;
        }
        return nodeName.toString();
    }

    private static List<List<PathArgument>> parseInputForMountPoint(final @NonNull String input,
                                                                    final @NonNull QNameModule startQNameModule,
                                                                    final DataSchemaContextNode<?> startNode,
                                                                    @NonNull List<List<PathArgument>> parsed,
                                                                    final @NonNull SchemaContext context) {
        DataSchemaContextNode<?> currentNode = startNode;
        int currentPosition = 0;
        int startPosition = 0;
        final int startSize = parsed.size();
        int closingParenthesis = 0;
        QNameModule currentQNameModule = startQNameModule;
        char lastSeparator = Character.MIN_VALUE;
        int augSize = parsed.size();
        QName augChildQName = null;
        DataSchemaContextNode<?> augNode = null;
        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);
            if (ParserConstants.YANG_IDENTIFIER_PART.matches(currentChar)) {
                currentPosition++;
                continue;
            }
            switch (currentChar) {
                case '/':
                    if (lastSeparator == ';' || input.charAt(closingParenthesis) == ')') {
                        parsed.add(new ArrayList<>());
                    }
                    lastSeparator = currentChar;
                    QName slashQName = QName.create(currentQNameModule,
                            input.substring(startPosition, currentPosition));
                    if (augNode != null && augNode.getChild(slashQName) != null && augChildQName == null) {
                        augChildQName = slashQName;
                    }
                    currentNode = resolvePath(parsed.get(parsed.size() - 1), slashQName, currentNode);
                    currentPosition++;
                    break;
                case ';':
                    if (lastSeparator == ';' || input.charAt(closingParenthesis) == ')') {
                        parsed.add(new ArrayList<>());
                    }
                    lastSeparator = currentChar;
                    resolvePath(parsed.get(parsed.size() - 1),
                            QName.create(currentQNameModule, input.substring(startPosition, currentPosition)),
                            currentNode);
                    currentPosition++;
                    break;
                case ':':
                    currentQNameModule = context.findModules(input.substring(startPosition, currentPosition))
                            .iterator().next().getQNameModule();
                    augNode = resolveMountPointMixinNode(currentNode, new ArrayList<>(), currentNode.getIdentifier()
                            .getNodeType()).getChild(QName.create(currentQNameModule,
                            findNextNodeName(input.substring(currentPosition + 1))));
                    augSize = parsed.size();
                    currentPosition++;
                    break;
                case '(':
                    if (lastSeparator == ';' || input.charAt(closingParenthesis) == ')') {
                        parsed.add(new ArrayList<>());
                    }
                    lastSeparator = currentChar;
                    // call with child node as new start node
                    closingParenthesis = currentPosition
                            + findClosingParenthesis(input.substring(currentPosition + 1));
                    QName parenthesisQName = QName.create(currentQNameModule,
                            input.substring(startPosition, currentPosition));
                    if (augNode != null && augNode.getChild(parenthesisQName) != null && augChildQName == null) {
                        augChildQName = parenthesisQName;
                    }
                    parsed = parseInputForMountPoint(
                            input.substring(currentPosition + 1, closingParenthesis),
                            currentQNameModule,
                            resolvePath(parsed.get(parsed.size() - 1), parenthesisQName, currentNode), parsed, context);
                    currentPosition = closingParenthesis + 1;
                    // closing parenthesis must be at the end of input or separator and one more character is expected
                    if (currentPosition != input.length()) {
                        if (currentPosition + 1 < input.length()) {
                            if (input.charAt(currentPosition) == ';') {
                                currentPosition++;
                            } else {
                                throw new RestconfDocumentedException(
                                        "Missing semicolon character in child nodes",
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
                default:
                    throw new RestconfDocumentedException(
                            "Unexpected character '" + currentChar + "' found in fields parameter value",
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
            startPosition = currentPosition;
        }
        // parse input to end
        if (startPosition < input.length()) {
            QName childQName = QName.create(currentQNameModule, input.substring(startPosition, currentPosition));
            if (lastSeparator == ';' || input.charAt(closingParenthesis) == ')') {
                parsed.add(new ArrayList<>());
            }
            resolvePath(parsed.get(parsed.size() - 1), childQName, currentNode);
        }
        if (augNode != null && augChildQName != null) {
            for (List<PathArgument> path : parsed.subList(augSize, parsed.size())) {
                if (!path.contains(augNode.getIdentifier())) {
                    path.add(0, augNode.getIdentifier());
                }
                if (!path.contains(augNode.getChild(augChildQName).getIdentifier())) {
                    path.add(0, augNode.getChild(augChildQName).getIdentifier());
                }
            }
        }

        if (startNode == null || startNode.isLeaf()) {
            return parsed;
        }

        for (List<PathArgument> path : parsed.subList(startSize - 1, parsed.size())) {
            if (!path.contains(startNode.getIdentifier())) {
                path.add(0, startNode.getIdentifier());
            }
            if (startNode.getDataSchemaNode() instanceof ListSchemaNode && !path.contains(YangInstanceIdentifier
                    .of(startNode.getDataSchemaNode().getQName()).getLastPathArgument())) {
                path.add(0, YangInstanceIdentifier.of(startNode.getDataSchemaNode().getQName())
                        .getLastPathArgument());
            }
        }
        return parsed;
    }
}
