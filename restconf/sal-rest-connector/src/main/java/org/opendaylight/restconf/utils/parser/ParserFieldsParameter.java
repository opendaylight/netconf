/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameter {
    public static @Nonnull List<PathArgument> parseFieldsParameter(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                                   @Nonnull final String input) {
        final List<YangInstanceIdentifier> parsed = new ArrayList<>();
        final SchemaContext context = identifier.getSchemaContext();
        final String startNamespace = identifier.getSchemaNode().getQName().getNamespace().toString();
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());
        Preconditions.checkNotNull(startNode, "Start node missing");

        parseInput(input, startNamespace, startNode, parsed, context);

        final List<YangInstanceIdentifier.PathArgument> result = new ArrayList<>();
        parsed.forEach(x -> result.addAll(x.getPathArguments()));

        return result;
    }

    private static void parseInput(@Nonnull final String input,
                                   @Nonnull final String startNamespace,
                                   @Nonnull final DataSchemaContextNode<?> startNode,
                                   @Nonnull final List<YangInstanceIdentifier> parsed,
                                   @Nonnull final SchemaContext context) {
        int currentPosition = 0;
        int startPosition = 0;
        DataSchemaContextNode<?> currentNode = startNode;
        String namespace = startNamespace;

        while (currentPosition < input.length()) {
            final char currentChar = input.charAt(currentPosition);

            if (Deserializer.IDENTIFIER.matches(currentChar) || currentChar == '/') {
                if (currentChar == '/') {
                    currentNode = addChildToResult(
                            currentNode, input.substring(startPosition, currentPosition), namespace, parsed);

                    currentPosition++;
                    startPosition = currentPosition;
                }

                currentPosition++;
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
                    // add current child to parsed results
                    final DataSchemaContextNode<?> child = addChildToResult(
                            currentNode, input.substring(startPosition, currentPosition), namespace, parsed);
                    // call with child node as new start node
                    parseInput(
                            input.substring(currentPosition + 1, input.indexOf(')')),
                            namespace,
                            child,
                            parsed,
                            context);

                    currentPosition = input.indexOf(')') + 1;
                    break;
                case ';':
                    // complete identifier found
                    addChildToResult(currentNode, input.substring(startPosition, currentPosition), namespace, parsed);
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
            addChildToResult(currentNode, input.substring(startPosition), namespace, parsed);
        }
    }

    private static @Nonnull DataSchemaContextNode<?> addChildToResult(
            @Nonnull final DataSchemaContextNode<?> currentNode,
            @Nonnull final String identifier,
            @Nonnull final String namespace,
            @Nonnull final List<YangInstanceIdentifier> parsed) {
        final QName childQName = QName.create(
                namespace,
                identifier,
                currentNode.getIdentifier().getNodeType().getRevision());

        final InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        DataSchemaContextNode<?> child = currentNode.getChild(childQName);

        while (child != null && child.isMixin()) {
            builder.node(child.getIdentifier());
            parsed.add(builder.build());

            child = child.getChild(childQName);
        }

        if (child == null) {
            throw new RestconfDocumentedException(
                    "Child " + identifier + " node missing in " + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        builder.node(child.getIdentifier());
        parsed.add(builder.build());

        return child;
    }
}
