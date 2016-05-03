/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedList;
import java.util.List;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for
 * restconf.
 *
 */
public final class YangInstanceIdentifierDeserializer {

    private YangInstanceIdentifierDeserializer() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Method to create {@link Iterable} from {@link PathArgument} which are
     * parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext
     *            - for validate of parsing path arguments
     * @param data
     *            - path to data
     * @return {@link Iterable} of {@link PathArgument}
     */
    public static Iterable<PathArgument> create(final SchemaContext schemaContext, final String data) {
        final List<PathArgument> path = new LinkedList<>();
        DataSchemaContextNode<?> current = DataSchemaContextTree.from(schemaContext).getRoot();
        int offset = 0;

        while (!allCharsConsumed(offset, data)) {
            offset = validArg(offset, data);
            int start = offset;
            checkValid(ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR.matches(currentChar(offset, data)),
                    "Identifier must start with character from set 'a-zA-Z_'", data, offset);
            offset = nextSequenceEnd(ParserBuilderConstants.Deserializer.IDENTIFIER, offset, data);

            final String preparedPrefix = data.substring(start, offset);
            final String prefix, localName;

            QName qname = null;
            switch (currentChar(offset, data)) {
                case ParserBuilderConstants.Deserializer.COLON:
                    prefix = preparedPrefix;
                    offset = skipCurrentChar(offset);
                    start = offset;
                    checkValid(
                            ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR
                                    .matches(currentChar(offset, data)),
                            "Identifier must start with character from set 'a-zA-Z_'", data, offset);

                    while (!allCharsConsumed(offset, data)
                            && ParserBuilderConstants.Deserializer.IDENTIFIER.matches(data.charAt(offset))) {
                        offset++;
                    }
                    localName = data.substring(start, offset);

                    final Module module = moduleForPrefix(prefix, schemaContext);
                    Preconditions.checkArgument(module != null, "Failed to lookup prefix %s", prefix);

                    qname = QName.create(module.getQNameModule(), localName);
                    break;
                case ParserBuilderConstants.Deserializer.EQUAL:
                    prefix = preparedPrefix;
                    qname = getQNameOfDataSchemaNode(prefix, current);
                    break;
                default:
                    throw new IllegalArgumentException("Failed build path.");
            }

            if (!allCharsConsumed(offset, data) && (currentChar(offset, data) == RestconfConstants.SLASH)) {
                current = prepareIdentifier(qname, current, path, data, offset);
                path.add(current.getIdentifier());
            } else if (!allCharsConsumed(offset, data)
                    && (currentChar(offset, data) == ParserBuilderConstants.Deserializer.EQUAL)) {
                current = nextContextNode(qname, current, path, offset, data);
                if (!current.isKeyedEntry()) {
                    offset = prepareNodeWithValue(qname, offset, data, path);
                } else {
                    final List<QName> keyDefinitions = ((ListSchemaNode) current.getDataSchemaNode())
                        .getKeyDefinition();
                    final ImmutableMap.Builder<QName, Object> keyValues = ImmutableMap.builder();

                    for (final QName keyQName : keyDefinitions) {
                        offset = skipCurrentChar(offset);
                        String value = null;
                        if ((currentChar(offset, data) == ParserBuilderConstants.Deserializer.COMMA)
                                || (currentChar(offset, data) == RestconfConstants.SLASH)) {
                            value = ParserBuilderConstants.Deserializer.EMPTY_STRING;
                        } else {
                            start = offset;
                            checkValid(
                                    ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR_PREDICATE
                                            .matches(currentChar(offset, data)),
                                    "Identifier must start with character from set 'a-zA-Z_'", data, offset);
                            offset = nextSequenceEnd(ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE, offset,
                                    data);
                            value = data.substring(start, offset);
                        }
                        value = findAndParsePercentEncoded(value);
                        keyValues.put(keyQName, value);
                    }
                    path.add(new YangInstanceIdentifier.NodeIdentifierWithPredicates(qname, keyValues.build()));
                }
            } else {
                throw new IllegalArgumentException(
                        "Bad char " + currentChar(offset, data) + " on position " + offset + ".");
            }
        }
        return ImmutableList.copyOf(path);
    }

    private static int nextSequenceEnd(final CharMatcher matcher, final int offset, final String data) {
        int offset_new = offset;
        while (!allCharsConsumed(offset_new, data) && matcher.matches(data.charAt(offset_new))) {
            offset_new += 1;
        }
        return offset_new;
    }

    private static int prepareNodeWithValue(final QName qname, int offset, final String data,
            final List<PathArgument> path) {
        offset = skipCurrentChar(offset);
        final int start = offset;
        checkValid(ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR.matches(currentChar(offset, data)),
                "Identifier must start with character from set 'a-zA-Z_'", data, offset);
        offset = nextSequenceEnd(ParserBuilderConstants.Deserializer.IDENTIFIER, offset, data);
        String value = data.substring(start, offset);
        value = findAndParsePercentEncoded(value);
        path.add(new YangInstanceIdentifier.NodeWithValue<>(qname, value));
        return offset;
    }

    private static DataSchemaContextNode<?> prepareIdentifier(final QName qname, final DataSchemaContextNode<?> current,
            final List<PathArgument> path, final String data, final int offset) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname, current, path, offset, data);
        checkValid(!currentNode.isKeyedEntry(), "Entry " + qname + " requires key or value predicate to be present",
                data, offset);
        return currentNode;
    }

    private static DataSchemaContextNode<?> nextContextNode(final QName qname, DataSchemaContextNode<?> current,
            final List<PathArgument> path, final int data, final String offset) {
        current = current.getChild(qname);
        checkValid(current != null, qname + " is not correct schema node identifier.", offset, data);
        while (current.isMixin()) {
            path.add(current.getIdentifier());
            current = current.getChild(qname);
        }
        return current;
    }

    private static String findAndParsePercentEncoded(String preparedPrefix) {
        if (!preparedPrefix.contains(String.valueOf(ParserBuilderConstants.Deserializer.PERCENT_ENCODING))) {
            return preparedPrefix;
        }
        final StringBuilder newPrefix = new StringBuilder();
        int i = 0;
        int startPoint = 0;
        int endPoint = preparedPrefix.length();
        while ((i = preparedPrefix.indexOf(ParserBuilderConstants.Deserializer.PERCENT_ENCODING)) != -1) {
            newPrefix.append(preparedPrefix.substring(startPoint, i));
            startPoint = i;
            startPoint++;
            final String hex = preparedPrefix.substring(startPoint, startPoint + 2);
            startPoint += 2;
            newPrefix.append((char) Integer.parseInt(hex, 16));
            preparedPrefix = preparedPrefix.substring(startPoint, endPoint);
            startPoint = 0;
            endPoint = preparedPrefix.length();
        }
        return newPrefix.toString();
    }

    private static QName getQNameOfDataSchemaNode(final String nodeName, final DataSchemaContextNode<?> current) {
        final DataSchemaNode dataSchemaNode = current.getDataSchemaNode();
        if (dataSchemaNode instanceof ContainerSchemaNode) {
            final ContainerSchemaNode contSchemaNode = (ContainerSchemaNode) dataSchemaNode;
            final DataSchemaNode node = RestconfSchemaUtil.findSchemaNodeInCollection(contSchemaNode.getChildNodes(),
                    nodeName);
            return node.getQName();
        } else if (dataSchemaNode instanceof ListSchemaNode) {
            final ListSchemaNode listSchemaNode = (ListSchemaNode) dataSchemaNode;
            final DataSchemaNode node = RestconfSchemaUtil.findSchemaNodeInCollection(listSchemaNode.getChildNodes(),
                    nodeName);
            return node.getQName();
        }
        throw new UnsupportedOperationException();
    }

    private static Module moduleForPrefix(final String prefix, final SchemaContext schemaContext) {
        return schemaContext.findModuleByName(prefix, null);
    }

    private static int validArg(int offset, final String data) {
        checkValid(RestconfConstants.SLASH == currentChar(offset, data), "Identifier must start with '/'.", data,
                offset);
        offset = skipCurrentChar(offset);
        checkValid(!allCharsConsumed(offset, data), "Identifier cannot end with '/'.", data, offset);
        return offset;
    }

    private static int skipCurrentChar(final int offset) {
        final int offset_new = offset + 1;
        return offset_new;
    }

    private static char currentChar(final int offset, final String data) {
        return data.charAt(offset);
    }

    private static void checkValid(final boolean condition, final String errorMsg, final String data,
            final int offset) {
        Preconditions.checkArgument(condition, "Could not parse Instance Identifier '%s'. Offset: %s : Reason: %s",
                data, offset, errorMsg);
    }

    private static boolean allCharsConsumed(final int offset, final String data) {
        return offset == data.length();
    }

}
