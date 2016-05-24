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
        final int offset = 0;
        final MainVarsWrapper variables = new YangInstanceIdentifierDeserializer.MainVarsWrapper(data,
                current, offset, schemaContext);

        while (!allCharsConsumed(variables)) {
            validArg(variables);
            final QName qname = prepareQName(variables);

            if (allCharsConsumed(variables) ||
                    currentChar(variables.getOffset(), variables.getData()) == RestconfConstants.SLASH) {
                prepareIdentifier(qname, path, variables);
                path.add(variables.getCurrent().getIdentifier());
            } else if (currentChar(variables.getOffset(),
                    variables.getData()) == ParserBuilderConstants.Deserializer.EQUAL) {
                current = nextContextNode(qname, path, variables);
                if (!current.isKeyedEntry()) {
                    prepareNodeWithValue(qname, path, variables);
                } else {
                    prepareNodeWithPredicates(qname, path, variables);
                }
            } else {
                throw new IllegalArgumentException(
                        "Bad char " + currentChar(offset, data) + " on position " + offset + ".");
            }
        }

        return ImmutableList.copyOf(path);
    }

    private static void prepareNodeWithPredicates(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        final List<QName> keyDefinitions = ((ListSchemaNode) variables.getCurrent().getDataSchemaNode())
                .getKeyDefinition();
        final ImmutableMap.Builder<QName, Object> keyValues = ImmutableMap.builder();

        for (final QName keyQName : keyDefinitions) {
            skipCurrentChar(variables);
            String value = null;
            if ((currentChar(variables.getOffset(), variables.getData()) == ParserBuilderConstants.Deserializer.COMMA)
                    || (currentChar(variables.getOffset(), variables.getData()) == RestconfConstants.SLASH)) {
                value = ParserBuilderConstants.Deserializer.EMPTY_STRING;
            } else {
                value = nextIdentifierFromNextSequence(ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE,
                        variables);
            }
            value = findAndParsePercentEncoded(value);
            keyValues.put(keyQName, value);
        }
        path.add(new YangInstanceIdentifier.NodeIdentifierWithPredicates(qname, keyValues.build()));
    }

    private static QName prepareQName(final MainVarsWrapper variables) {
        checkValid(
                ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR
                        .matches(currentChar(variables.getOffset(), variables.getData())),
                "Identifier must start with character from set 'a-zA-Z_'", variables.getData(), variables.getOffset());
        final String preparedPrefix = nextIdentifierFromNextSequence(ParserBuilderConstants.Deserializer.IDENTIFIER, variables);
        final String prefix, localName;

        switch (currentChar(variables.getOffset(), variables.getData())) {
            case ParserBuilderConstants.Deserializer.COLON:
                prefix = preparedPrefix;
                skipCurrentChar(variables);
                checkValid(
                        ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR
                                .matches(currentChar(variables.getOffset(), variables.getData())),
                        "Identifier must start with character from set 'a-zA-Z_'", variables.getData(),
                        variables.getOffset());
                localName = nextIdentifierFromNextSequence(ParserBuilderConstants.Deserializer.IDENTIFIER, variables);

                final Module module = moduleForPrefix(prefix, variables.getSchemaContext());
                Preconditions.checkArgument(module != null, "Failed to lookup prefix %s", prefix);

                return QName.create(module.getQNameModule(), localName);
            case ParserBuilderConstants.Deserializer.EQUAL:
                prefix = preparedPrefix;
                return getQNameOfDataSchemaNode(prefix, variables);
            default:
                throw new IllegalArgumentException("Failed build path.");
        }
    }

    private static String nextIdentifierFromNextSequence(final CharMatcher matcher, final MainVarsWrapper variables) {
        final int start = variables.getOffset();
        nextSequenceEnd(matcher, variables);
        return variables.getData().substring(start, variables.getOffset());
    }

    private static void nextSequenceEnd(final CharMatcher matcher, final MainVarsWrapper variables) {
        while (!allCharsConsumed(variables)
                && matcher.matches(variables.getData().charAt(variables.getOffset()))) {
            variables.setOffset(variables.getOffset() + 1);
        }
    }

    private static void prepareNodeWithValue(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        skipCurrentChar(variables);
        String value = nextIdentifierFromNextSequence(ParserBuilderConstants.Deserializer.IDENTIFIER, variables);
        value = findAndParsePercentEncoded(value);
        path.add(new YangInstanceIdentifier.NodeWithValue<>(qname, value));
    }

    private static void prepareIdentifier(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname, path, variables);
        checkValid(!currentNode.isKeyedEntry(), "Entry " + qname + " requires key or value predicate to be present",
                variables.getData(), variables.getOffset());
    }

    private static DataSchemaContextNode<?> nextContextNode(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        variables.setCurrent(variables.getCurrent().getChild(qname));
        DataSchemaContextNode<?> current = variables.getCurrent();
        checkValid(current != null, qname + " is not correct schema node identifier.", variables.getData(),
                variables.getOffset());
        while (current.isMixin()) {
            path.add(current.getIdentifier());
            current = current.getChild(qname);
            variables.setCurrent(current);
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

    private static QName getQNameOfDataSchemaNode(final String nodeName, final MainVarsWrapper variables) {
        final DataSchemaNode dataSchemaNode = variables.getCurrent().getDataSchemaNode();
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

    private static void validArg(final MainVarsWrapper variables) {
        checkValid(RestconfConstants.SLASH == currentChar(variables.getOffset(), variables.getData()),
                "Identifier must start with '/'.", variables.getData(), variables.getOffset());
        skipCurrentChar(variables);
        checkValid(!allCharsConsumed(variables), "Identifier cannot end with '/'.",
                variables.getData(), variables.getOffset());
    }

    private static void skipCurrentChar(final MainVarsWrapper variables) {
        variables.setOffset(variables.getOffset() + 1);
    }

    private static char currentChar(final int offset, final String data) {
        return data.charAt(offset);
    }

    private static void checkValid(final boolean condition, final String errorMsg, final String data,
            final int offset) {
        Preconditions.checkArgument(condition, "Could not parse Instance Identifier '%s'. Offset: %s : Reason: %s",
                data, offset, errorMsg);
    }

    private static boolean allCharsConsumed(final MainVarsWrapper variables) {
        return variables.getOffset() == variables.getData().length();
    }

    private static class MainVarsWrapper {

        private final SchemaContext schemaContext;
        private final String data;
        private DataSchemaContextNode<?> current;
        private int offset;

        public MainVarsWrapper(final String data, final DataSchemaContextNode<?> current, final int offset,
                final SchemaContext schemaContext) {
            this.data = data;
            this.schemaContext = schemaContext;
            this.setCurrent(current);
            this.setOffset(offset);
        }

        public String getData() {
            return this.data;
        }

        public DataSchemaContextNode<?> getCurrent() {
            return this.current;
        }

        public void setCurrent(final DataSchemaContextNode<?> current) {
            this.current = current;
        }

        public int getOffset() {
            return this.offset;
        }

        public void setOffset(final int offset) {
            this.offset = offset;
        }

        public SchemaContext getSchemaContext() {
            return this.schemaContext;
        }

    }
}
