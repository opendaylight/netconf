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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.opendaylight.netconf.md.sal.rest.common.RestconfValidationUtils;
import org.opendaylight.netconf.sal.rest.impl.RestUtil;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;

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
     *             for validate of parsing path arguments
     * @param data
     *             path to data
     * @return {@link Iterable} of {@link PathArgument}
     */
    public static Iterable<PathArgument> create(final SchemaContext schemaContext, final String data) {
        final List<PathArgument> path = new LinkedList<>();
        final MainVarsWrapper variables = new YangInstanceIdentifierDeserializer.MainVarsWrapper(
                data, DataSchemaContextTree.from(schemaContext).getRoot(),
                YangInstanceIdentifierDeserializer.MainVarsWrapper.STARTING_OFFSET, schemaContext);

        while (!allCharsConsumed(variables)) {
            validArg(variables);
            final QName qname = prepareQName(variables);

            // this is the last identifier (input is consumed) or end of identifier (slash)
            if (allCharsConsumed(variables)
                    || (currentChar(variables.getOffset(), variables.getData()) == RestconfConstants.SLASH)) {
                prepareIdentifier(qname, path, variables);
                if (variables.getCurrent() == null) {
                    path.add(NodeIdentifier.create(qname));
                } else {
                    path.add(variables.getCurrent().getIdentifier());
                }
            } else if (currentChar(variables.getOffset(),
                    variables.getData()) == ParserBuilderConstants.Deserializer.EQUAL) {
                if (nextContextNode(qname, path, variables).getDataSchemaNode() instanceof ListSchemaNode) {
                    prepareNodeWithPredicates(qname, path, variables);
                } else {
                    prepareNodeWithValue(qname, path, variables);
                }
            } else {
                throw new IllegalArgumentException(
                        "Bad char " + currentChar(variables.getOffset(), variables.getData()) + " on position "
                                + variables.getOffset() + ".");
            }
        }

        return ImmutableList.copyOf(path);
    }

    private static void prepareNodeWithPredicates(final QName qname, final List<PathArgument> path,
                                                  final MainVarsWrapper variables) {

        final DataSchemaNode dataSchemaNode = variables.getCurrent().getDataSchemaNode();
        checkValid((dataSchemaNode != null), "Data schema node is null", variables.getData(), variables.getOffset());

        final Iterator<QName> keys = ((ListSchemaNode) dataSchemaNode).getKeyDefinition().iterator();
        final ImmutableMap.Builder<QName, Object> values = ImmutableMap.builder();

        // skip already expected equal sign
        skipCurrentChar(variables);

        // read key value separated by comma
        while (keys.hasNext() && !allCharsConsumed(variables) && (currentChar(variables.getOffset(),
                variables.getData()) != RestconfConstants.SLASH)) {

            // empty key value
            if (currentChar(variables.getOffset(), variables.getData()) == ParserBuilderConstants.Deserializer.COMMA) {
                values.put(keys.next(), ParserBuilderConstants.Deserializer.EMPTY_STRING);
                skipCurrentChar(variables);
                continue;
            }

            // check if next value is parsable
            RestconfValidationUtils.checkDocumentedError(
                    ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE
                            .matches(currentChar(variables.getOffset(), variables.getData())),
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.MALFORMED_MESSAGE,
                    ""
            );

            // parse value
            final QName key = keys.next();
            DataSchemaNode leafSchemaNode = null;
            if (dataSchemaNode instanceof ListSchemaNode) {
                leafSchemaNode = ((ListSchemaNode) dataSchemaNode).getDataChildByName(key);
            } else if (dataSchemaNode instanceof LeafListSchemaNode) {
                leafSchemaNode = dataSchemaNode;
            }
            final String value = findAndParsePercentEncoded(nextIdentifierFromNextSequence(
                    ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE, variables));
            final Object valueByType = prepareValueByType(leafSchemaNode, value, variables);
            values.put(key, valueByType);


            // skip comma
            if (keys.hasNext() && !allCharsConsumed(variables) && (currentChar(
                    variables.getOffset(), variables.getData()) == ParserBuilderConstants.Deserializer.COMMA)) {
                skipCurrentChar(variables);
            }
        }

        // the last key is considered to be empty
        if (keys.hasNext()) {
            if (allCharsConsumed(variables)
                    || (currentChar(variables.getOffset(), variables.getData()) == RestconfConstants.SLASH)) {
                values.put(keys.next(), ParserBuilderConstants.Deserializer.EMPTY_STRING);
            }

            // there should be no more missing keys
            RestconfValidationUtils.checkDocumentedError(
                    !keys.hasNext(),
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.MISSING_ATTRIBUTE,
                    "Key value missing for: " + qname
            );
        }

        path.add(new YangInstanceIdentifier.NodeIdentifierWithPredicates(qname, values.build()));
    }

    private static Object prepareValueByType(final DataSchemaNode schemaNode, final String value,
            final MainVarsWrapper vars) {
        Object decoded = null;

        TypeDefinition<? extends TypeDefinition<?>> typedef = null;
        if (schemaNode instanceof LeafListSchemaNode) {
            typedef = ((LeafListSchemaNode) schemaNode).getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) baseType, vars.getSchemaContext(),
                    schemaNode);
        }
        final Codec<Object, Object> codec = RestCodec.from(typedef, null);
        decoded = codec.deserialize(value);
        if (decoded == null) {
            if ((baseType instanceof IdentityrefTypeDefinition)) {
                decoded = toQName(value, schemaNode, vars.getSchemaContext());
            }
        }
        return decoded;
    }

    private static Object toQName(final String value, final DataSchemaNode schemaNode,
            final SchemaContext schemaContext) {
        final String moduleName = toModuleName(value);
        final String nodeName = toNodeName(value);
        final Module module = schemaContext.findModuleByName(moduleName, null);
        for (final IdentitySchemaNode identitySchemaNode : module.getIdentities()) {
            final QName qName = identitySchemaNode.getQName();
            if (qName.getLocalName().equals(nodeName)) {
                return qName;
            }
        }
        return QName.create(schemaNode.getQName().getNamespace(), schemaNode.getQName().getRevision(), nodeName);
    }

    private static String toNodeName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return str;
        }

        if (str.indexOf(':', idx + 1) != -1) {
            return str;
        }

        return str.substring(idx + 1);
    }

    private static String toModuleName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return null;
        }

        if (str.indexOf(':', idx + 1) != -1) {
            return null;
        }

        return str.substring(0, idx);
    }

    private static QName prepareQName(final MainVarsWrapper variables) {
        checkValid(
                ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR
                        .matches(currentChar(variables.getOffset(), variables.getData())),
                "Identifier must start with character from set 'a-zA-Z_'", variables.getData(), variables.getOffset());
        final String preparedPrefix = nextIdentifierFromNextSequence(
                ParserBuilderConstants.Deserializer.IDENTIFIER, variables);
        final String prefix;
        final String localName;

        if (allCharsConsumed(variables)) {
            return getQNameOfDataSchemaNode(preparedPrefix, variables);
        }

        switch (currentChar(variables.getOffset(), variables.getData())) {
            case RestconfConstants.SLASH:
                prefix = preparedPrefix;
                return getQNameOfDataSchemaNode(prefix, variables);
            case ParserBuilderConstants.Deserializer.COLON:
                prefix = preparedPrefix;
                skipCurrentChar(variables);
                checkValid(
                        ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR
                                .matches(currentChar(variables.getOffset(), variables.getData())),
                        "Identifier must start with character from set 'a-zA-Z_'", variables.getData(),
                        variables.getOffset());
                localName = nextIdentifierFromNextSequence(ParserBuilderConstants.Deserializer.IDENTIFIER, variables);

                if (!allCharsConsumed(variables) && (currentChar(
                        variables.getOffset(), variables.getData()) == ParserBuilderConstants.Deserializer.EQUAL)) {
                    return getQNameOfDataSchemaNode(localName, variables);
                } else {
                    final Module module = moduleForPrefix(prefix, variables.getSchemaContext());
                    Preconditions.checkArgument(module != null, "Failed to lookup prefix %s", prefix);
                    return QName.create(module.getQNameModule(), localName);
                }
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
        final String value = nextIdentifierFromNextSequence(
                ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE, variables);

        // exception if value attribute is missing
        RestconfValidationUtils.checkDocumentedError(
                !value.isEmpty(),
                RestconfError.ErrorType.PROTOCOL,
                RestconfError.ErrorTag.MISSING_ATTRIBUTE,
                "Value missing for: " + qname
        );
        final DataSchemaNode dataSchemaNode = variables.getCurrent().getDataSchemaNode();
        final Object valueByType = prepareValueByType(dataSchemaNode, findAndParsePercentEncoded(value), variables);
        path.add(new YangInstanceIdentifier.NodeWithValue<>(qname, valueByType));
    }

    private static void prepareIdentifier(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname, path, variables);
        if (currentNode == null) {
            return;
        }
        checkValid(!currentNode.isKeyedEntry(), "Entry " + qname + " requires key or value predicate to be present",
                variables.getData(), variables.getOffset());
    }

    private static DataSchemaContextNode<?> nextContextNode(final QName qname, final List<PathArgument> path,
            final MainVarsWrapper variables) {
        variables.setCurrent(variables.getCurrent().getChild(qname));
        DataSchemaContextNode<?> current = variables.getCurrent();
        if (current == null) {
            for (final RpcDefinition rpcDefinition : variables.getSchemaContext()
                    .findModuleByNamespaceAndRevision(qname.getNamespace(), qname.getRevision()).getRpcs()) {
                if (rpcDefinition.getQName().getLocalName().equals(qname.getLocalName())) {
                    return null;
                }
            }
        }
        checkValid(current != null, qname + " is not correct schema node identifier.", variables.getData(),
                variables.getOffset());
        while (current.isMixin()) {
            path.add(current.getIdentifier());
            current = current.getChild(qname);
            variables.setCurrent(current);
        }
        return current;
    }

    private static String findAndParsePercentEncoded(final String preparedPrefix) {
        if (!preparedPrefix.contains(String.valueOf(ParserBuilderConstants.Deserializer.PERCENT_ENCODING))) {
            return preparedPrefix;
        }

        final StringBuilder parsedPrefix = new StringBuilder(preparedPrefix);
        final CharMatcher matcher = CharMatcher.is(ParserBuilderConstants.Deserializer.PERCENT_ENCODING);

        while (matcher.matchesAnyOf(parsedPrefix)) {
            final int percentCharPosition = matcher.indexIn(parsedPrefix);
            parsedPrefix.replace(
                    percentCharPosition,
                    percentCharPosition + ParserBuilderConstants.Deserializer.LAST_ENCODED_CHAR,
                    String.valueOf((char) Integer.parseInt(parsedPrefix.substring(
                            percentCharPosition + ParserBuilderConstants.Deserializer.FIRST_ENCODED_CHAR,
                            percentCharPosition + ParserBuilderConstants.Deserializer.LAST_ENCODED_CHAR),
                            ParserBuilderConstants.Deserializer.PERCENT_ENCODED_RADIX)));
        }

        return parsedPrefix.toString();
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
        // every identifier except of the first MUST start with slash
        if (variables.getOffset() != MainVarsWrapper.STARTING_OFFSET) {
            checkValid(RestconfConstants.SLASH == currentChar(variables.getOffset(), variables.getData()),
                    "Identifier must start with '/'.", variables.getData(), variables.getOffset());

            // skip slash
            skipCurrentChar(variables);

            // check if slash is not also the last char in identifier
            checkValid(!allCharsConsumed(variables), "Identifier cannot end with '/'.",
                    variables.getData(), variables.getOffset());
        }
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

    private static final class MainVarsWrapper {
        private static final int STARTING_OFFSET = 0;

        private final SchemaContext schemaContext;
        private final String data;

        private DataSchemaContextNode<?> current;
        private int offset;

        MainVarsWrapper(final String data, final DataSchemaContextNode<?> current, final int offset,
                final SchemaContext schemaContext) {
            this.data = data;
            this.current = current;
            this.offset = offset;
            this.schemaContext = schemaContext;
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
