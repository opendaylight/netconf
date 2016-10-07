/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameter {
    public static @Nonnull List<PathArgument> parseFieldsParameter(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                                   @Nonnull final String input) {
        final List<YangInstanceIdentifier> identifiers = new ArrayList<>();
        final SchemaContext context = identifier.getSchemaContext();
        final DataSchemaContextNode<?> currentNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());
        final String namespace = identifier.getSchemaNode().getQName().getNamespace().toString();

        parseInput(input, namespace, currentNode, identifiers, context);

        final List<YangInstanceIdentifier.PathArgument> result = new ArrayList<>();
        identifiers.forEach(x -> result.addAll(x.getPathArguments()));

        return result;
    }

    private static void parseInput(final String input,
                                   final String defaultNamespace,
                                   final DataSchemaContextNode<?> defaultNode,
                                   final List<YangInstanceIdentifier> results,
                                   final SchemaContext context) {
        int i = 0;
        int j = 0;
        DataSchemaContextNode<?> currentNode = defaultNode;
        String namespace = defaultNamespace;

        while (i < input.length()) {
            final char currentChar = input.charAt(i);

            if (Deserializer.IDENTIFIER.matches(currentChar) || currentChar == '/') {
                if (currentChar == '/') {
                    currentNode = findAndAddChildToResult(currentNode, input.substring(j, i), namespace);
                    results.add(YangInstanceIdentifier.create(currentNode.getIdentifier()));
                    j = i + 1;
                }

                continue;
            }

            switch (currentChar) {
                case ':' :
                    // new namespace found
                    namespace = context.findModuleByName(input.substring(j, i), null).getNamespace().toString();
                    break;
                case ';':
                    parseInput(input.substring(i), namespace, currentNode, results, context);
                    break;
                case '(':
                    // call with child defaultNode as current defaultNode
                    parseInput(
                            input.substring(i, input.indexOf(')')),
                            namespace,
                            findAndAddChildToResult(currentNode, input.substring(j, i), namespace),
                            results,
                            context);
                    break;
                default:
                    // complete identifier found
                    results.add(
                            YangInstanceIdentifier.create(
                                    findAndAddChildToResult(
                                            currentNode, input.substring(j, i), namespace).getIdentifier()));
                    break;
            }
        }
    }

    private static DataSchemaContextNode<?> findAndAddChildToResult(final DataSchemaContextNode<?> current,
                                                                    final String identifier,
                                                                    final String namespace) {
        final QName childQName = QName.create(
                namespace,
                identifier,
                current.getIdentifier().getNodeType().getRevision());

        return  current.getChild(childQName);
    }
}
