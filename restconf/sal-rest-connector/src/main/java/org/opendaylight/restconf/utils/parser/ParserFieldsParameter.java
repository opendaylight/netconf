/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameter {
    public static @Nonnull List<Set<QName>> parseFieldsParameter(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                                 @Nonnull final String input) {
        final List<Set<QName>> parsed = new LinkedList<>();
        final SchemaContext context = identifier.getSchemaContext();
        final String startNamespace = identifier.getSchemaNode().getQName().getNamespace().toString();
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        parseInput(input, startNamespace, startNode, parsed, context);
        return parsed;
    }

    private static void parseInput(@Nonnull final String input, @Nonnull final String startNamespace,
                                   @Nonnull final DataSchemaContextNode<?> startNode,
                                   @Nonnull final List<Set<QName>> parsed, @Nonnull final SchemaContext context) {
        int currentPosition = 0;
        int startPosition = 0;
        DataSchemaContextNode<?> currentNode = startNode;
        String namespace = startNamespace;

        Set<QName> currentLevel = new HashSet<>();
        parsed.add(currentLevel);

        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);

            if (Deserializer.IDENTIFIER.matches(currentChar) || currentChar == '/') {
                if (currentChar == '/') {
                    // add parsed identifier to results for current level
                    currentNode = addChildToResult(
                            currentNode, input.substring(startPosition, currentPosition), namespace, currentLevel);
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
                case ':' :
                    // new namespace found
                    namespace = context.findModuleByName(
                            input.substring(startPosition, currentPosition), null).getNamespace().toString();
                    currentPosition++;
                    break;
                case '(':
                    // add current child to parsed results for current level
                    final DataSchemaContextNode<?> child = addChildToResult(
                            currentNode, input.substring(startPosition, currentPosition), namespace, currentLevel);
                    // call with child node as new start node for one level down
                    int endingBracket = currentPosition + findClosingBracket(input.substring(currentPosition + 1));
                    parseInput(
                            input.substring(currentPosition + 1, endingBracket),
                            namespace,
                            child,
                            parsed,
                            context);

                    currentPosition = endingBracket + 1;
                    break;
                case ';':
                    // complete identifier found
                    addChildToResult(
                            currentNode, input.substring(startPosition, currentPosition), namespace, currentLevel);
                    currentPosition++;
                    break;
                default:
                    throw new RestconfDocumentedException("Unexpected parameter value: " + currentChar,
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            startPosition = currentPosition;
        }

        // parse input to end
        if (startPosition < input.length()) {
            addChildToResult(currentNode, input.substring(startPosition), namespace, currentLevel);
        }
    }

    /**
     * Return position of matching bracket found increased by one, but at most equals to input size
     * @return int
     */
    private static int findClosingBracket(final String input) {
        int position = 0;
        int count = 1;

        while (position < input.length()) {
            if (input.charAt(position) == '(') {
                count++;
            }

            if (input.charAt(position) == ')') {
                count--;
            }

            if (count == 0) {
                position++;
                break;
            }

            position++;
        }

        return position;
    }

    private static @Nonnull DataSchemaContextNode<?> addChildToResult(
            @Nonnull final DataSchemaContextNode<?> currentNode, @Nonnull final String identifier,
            @Nonnull final String namespace, @Nonnull final Set<QName> level) {
        final QName childQName = QName.create(
                namespace,
                identifier,
                currentNode.getIdentifier().getNodeType().getRevision());

        DataSchemaContextNode<?> child = currentNode.getChild(childQName);

        while (child != null && child.isMixin()) {
            level.add(child.getIdentifier().getNodeType());
            child = child.getChild(childQName);
        }

        if (child == null) {
            throw new RestconfDocumentedException(
                    "Child " + identifier + " node missing in "
                            + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        level.add(child.getIdentifier().getNodeType());
        return child;
    }
}
