/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants.SLASH;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.COLON;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.COMMA;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.EMPTY_STRING;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.EQUAL;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.FIRST_ENCODED_CHAR;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.IDENTIFIER;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.IDENTIFIER_PREDICATE;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.LAST_ENCODED_CHAR;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.PERCENT_ENCODED_RADIX;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer.PERCENT_ENCODING;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.restconf.common.util.RestconfSchemaUtil;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
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
    private final SchemaContext schemaContext;
    private final String data;

    private DataSchemaContextNode<?> current;
    private int offset;

    private YangInstanceIdentifierDeserializer(final SchemaContext schemaContext, final String data) {
        this.schemaContext = requireNonNull(schemaContext);
        this.data = requireNonNull(data);
        current = DataSchemaContextTree.from(schemaContext).getRoot();
    }

    /**
     * Method to create {@link Iterable} from {@link PathArgument} which are parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext for validate of parsing path arguments
     * @param data path to data, in URL string form
     * @return {@link Iterable} of {@link PathArgument}
     */
    public static Iterable<PathArgument> create(final SchemaContext schemaContext, final String data) {
        return new YangInstanceIdentifierDeserializer(schemaContext, data).parse();
    }

    private List<PathArgument> parse() {
        final List<PathArgument> path = new ArrayList<>();

        while (!allCharsConsumed()) {
            validArg();
            final QName qname = prepareQName();

            // this is the last identifier (input is consumed) or end of identifier (slash)
            if (allCharsConsumed() || currentChar() == SLASH) {
                prepareIdentifier(qname, path);
                path.add(current == null ? NodeIdentifier.create(qname) : current.getIdentifier());
            } else if (currentChar() == EQUAL) {
                if (nextContextNode(qname, path).getDataSchemaNode() instanceof ListSchemaNode) {
                    prepareNodeWithPredicates(qname, path, (ListSchemaNode) current.getDataSchemaNode());
                } else {
                    prepareNodeWithValue(qname, path);
                }
            } else {
                throw new RestconfDocumentedException(String.format("Failed to parse input identifier '%s' "
                        + "- Bad char '%c' on position '%d'.", data, currentChar(), offset),
                        RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.MALFORMED_MESSAGE);
            }
        }

        return ImmutableList.copyOf(path);
    }

    private void prepareNodeWithPredicates(final QName qname, final List<PathArgument> path,
            final ListSchemaNode listSchemaNode) {
        checkValid(listSchemaNode != null, "Data schema node is null", RestconfError.ErrorTag.MALFORMED_MESSAGE);

        final Iterator<QName> keys = listSchemaNode.getKeyDefinition().iterator();
        final ImmutableMap.Builder<QName, Object> values = ImmutableMap.builder();

        // skip already expected equal sign
        skipCurrentChar();

        // read key value separated by comma
        while (keys.hasNext() && !allCharsConsumed() && currentChar() != SLASH) {

            // empty key value
            if (currentChar() == COMMA) {
                values.put(keys.next(), EMPTY_STRING);
                skipCurrentChar();
                continue;
            }

            // check if next value is parsable
            RestconfDocumentedException.throwIf(!IDENTIFIER_PREDICATE.matches(currentChar()), "Value is not parsable.",
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.MALFORMED_MESSAGE);

            // parse value
            final QName key = keys.next();
            Optional<DataSchemaNode> leafSchemaNode = listSchemaNode.findDataChildByName(key);
            if (!leafSchemaNode.isPresent()) {
                throw new RestconfDocumentedException("Schema not found for " + key,
                        RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
            }

            final String value = findAndParsePercentEncoded(nextIdentifierFromNextSequence(IDENTIFIER_PREDICATE));
            final Object valueByType = prepareValueByType(leafSchemaNode.get(), value);
            values.put(key, valueByType);


            // skip comma
            if (keys.hasNext() && !allCharsConsumed() && currentChar() == COMMA) {
                skipCurrentChar();
            }
        }

        // the last key is considered to be empty
        if (keys.hasNext()) {
            // at this point, it must be true that current char is '/' or all chars have already been consumed
            values.put(keys.next(), EMPTY_STRING);

            // there should be no more missing keys
            RestconfDocumentedException.throwIf(keys.hasNext(),
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.MISSING_ATTRIBUTE,
                    "Cannot parse input identifier '%s'. Key value is missing for QName: %s",data, qname);
        }

        path.add(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(qname, values.build()));
    }

    private Object prepareValueByType(final DataSchemaNode schemaNode, final String value) {
        Object decoded;

        TypeDefinition<? extends TypeDefinition<?>> typedef;
        if (schemaNode instanceof LeafListSchemaNode) {
            typedef = ((LeafListSchemaNode) schemaNode).getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) baseType, schemaContext,
                    schemaNode);
        }
        decoded = RestCodec.from(typedef, null, schemaContext).deserialize(value);
        if (decoded == null) {
            if (baseType instanceof IdentityrefTypeDefinition) {
                decoded = toQName(value, schemaNode, schemaContext);
            }
        }
        return decoded;
    }

    private QName prepareQName() {
        checkValidIdentifierStart();
        final String preparedPrefix = nextIdentifierFromNextSequence(IDENTIFIER);
        final String prefix;
        final String localName;

        if (allCharsConsumed()) {
            return getQNameOfDataSchemaNode(preparedPrefix);
        }

        switch (currentChar()) {
            case SLASH:
            case EQUAL:
                prefix = preparedPrefix;
                return getQNameOfDataSchemaNode(prefix);
            case COLON:
                prefix = preparedPrefix;
                skipCurrentChar();
                checkValidIdentifierStart();
                localName = nextIdentifierFromNextSequence(IDENTIFIER);

                if (!allCharsConsumed() && currentChar() == EQUAL) {
                    return getQNameOfDataSchemaNode(localName);
                } else {
                    final Module module = moduleForPrefix(prefix);
                    if (module == null) {
                        throw new RestconfDocumentedException(String.format("Cannot parse input identifier '%s' - "
                                + "Failed to lookup for module with name '%s'.", data, prefix),
                                RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.UNKNOWN_ELEMENT);
                    }
                    return QName.create(module.getQNameModule(), localName);
                }
            default:
                throw new RestconfDocumentedException(String.format("Failed to parse input identifier '%s' "
                        + "- Bad char '%c' on position '%d'.", data, currentChar(), offset),
                        RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.MALFORMED_MESSAGE);
        }
    }

    private void prepareNodeWithValue(final QName qname, final List<PathArgument> path) {
        skipCurrentChar();
        final String value = nextIdentifierFromNextSequence(IDENTIFIER_PREDICATE);

        // exception if value attribute is missing
        RestconfDocumentedException.throwIf(
                value.isEmpty(),
                RestconfError.ErrorType.PROTOCOL,
                RestconfError.ErrorTag.MISSING_ATTRIBUTE,
                "Cannot parse input identifier '%s' - value is missing for QName: %s.", data, qname);
        final DataSchemaNode dataSchemaNode = current.getDataSchemaNode();
        final Object valueByType = prepareValueByType(dataSchemaNode, findAndParsePercentEncoded(value));
        path.add(new YangInstanceIdentifier.NodeWithValue<>(qname, valueByType));
    }

    private void prepareIdentifier(final QName qname, final List<PathArgument> path) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname, path);
        if (currentNode != null) {
            checkValid(!currentNode.isKeyedEntry(), String.format(
                "Entry '%s' requires key or value predicate to be present.", qname),
                RestconfError.ErrorTag.MISSING_ATTRIBUTE);
        }
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH",
            justification = "code does check for null 'current' but FB doesn't recognize it")
    private DataSchemaContextNode<?> nextContextNode(final QName qname, final List<PathArgument> path) {
        final DataSchemaContextNode<?> initialContext = current;
        final DataSchemaNode initialDataSchema = initialContext.getDataSchemaNode();

        current = initialContext.getChild(qname);

        if (current == null) {
            final Optional<Module> module = schemaContext.findModule(qname.getModule());
            if (module.isPresent()) {
                for (final RpcDefinition rpcDefinition : module.get().getRpcs()) {
                    if (rpcDefinition.getQName().getLocalName().equals(qname.getLocalName())) {
                        return null;
                    }
                }
            }
            if (findActionDefinition(initialDataSchema, qname.getLocalName()).isPresent()) {
                return null;
            }
        }
        checkValid(current != null, String.format("'%s' is not correct schema node identifier.", qname),
                RestconfError.ErrorTag.MALFORMED_MESSAGE);
        while (current.isMixin()) {
            path.add(current.getIdentifier());
            current = current.getChild(qname);
        }
        return current;
    }

    private Module moduleForPrefix(final String prefix) {
        return schemaContext.findModules(prefix).stream().findFirst().orElse(null);
    }

    private boolean allCharsConsumed() {
        return offset == data.length();
    }

    private void checkValid(final boolean condition, final String errorMsg, final RestconfError.ErrorTag errorTag) {
        RestconfDocumentedException.throwIf(!condition, RestconfError.ErrorType.PROTOCOL, errorTag,
            "Could not parse Instance Identifier '%s'. Offset: '%d' : Reason: %s", data, offset, errorMsg);
    }

    private void checkValidIdentifierStart() {
        checkValid(IDENTIFIER_FIRST_CHAR.matches(currentChar()),
                "Identifier must start with character from set 'a-zA-Z_'",
                RestconfError.ErrorTag.MALFORMED_MESSAGE);
    }

    private char currentChar() {
        return data.charAt(offset);
    }

    private void skipCurrentChar() {
        offset++;
    }

    private String nextIdentifierFromNextSequence(final CharMatcher matcher) {
        final int start = offset;
        while (!allCharsConsumed() && matcher.matches(currentChar())) {
            skipCurrentChar();
        }
        return data.substring(start, offset);
    }

    private void validArg() {
        // every identifier except of the first MUST start with slash
        if (offset != 0) {
            checkValid(SLASH == currentChar(), "Identifier must start with '/'.",
                    RestconfError.ErrorTag.MALFORMED_MESSAGE);

            // skip consecutive slashes, users often assume restconf URLs behave just as HTTP does by squashing
            // multiple slashes into a single one
            while (!allCharsConsumed() && SLASH == currentChar()) {
                skipCurrentChar();
            }

            // check if slash is not also the last char in identifier
            checkValid(!allCharsConsumed(), "Identifier cannot end with '/'.",
                    RestconfError.ErrorTag.MALFORMED_MESSAGE);
        }
    }

    private QName getQNameOfDataSchemaNode(final String nodeName) {
        final DataSchemaNode dataSchemaNode = current.getDataSchemaNode();
        if (dataSchemaNode instanceof ContainerSchemaNode) {
            return getQNameOfDataSchemaNode((ContainerSchemaNode) dataSchemaNode, nodeName);
        } else if (dataSchemaNode instanceof ListSchemaNode) {
            return getQNameOfDataSchemaNode((ListSchemaNode) dataSchemaNode, nodeName);
        }

        throw new UnsupportedOperationException("Unsupported schema node " + dataSchemaNode);
    }

    private static <T extends DataNodeContainer & SchemaNode & ActionNodeContainer> QName getQNameOfDataSchemaNode(
            final T parent, final String nodeName) {
        final Optional<ActionDefinition> actionDef = findActionDefinition(parent, nodeName);
        final SchemaNode node;
        if (actionDef.isPresent()) {
            node = actionDef.get();
        } else {
            node = RestconfSchemaUtil.findSchemaNodeInCollection(parent.getChildNodes(), nodeName);
        }
        return node.getQName();
    }

    private static Optional<ActionDefinition> findActionDefinition(final SchemaNode dataSchemaNode,
            final String nodeName) {
        requireNonNull(dataSchemaNode, "DataSchema Node must not be null.");
        if (dataSchemaNode instanceof ActionNodeContainer) {
            return ((ActionNodeContainer) dataSchemaNode).getActions().stream()
                    .filter(actionDef -> actionDef.getQName().getLocalName().equals(nodeName)).findFirst();
        }
        return Optional.empty();
    }

    private static String findAndParsePercentEncoded(final String preparedPrefix) {
        if (preparedPrefix.indexOf(PERCENT_ENCODING) == -1) {
            return preparedPrefix;
        }

        final StringBuilder parsedPrefix = new StringBuilder(preparedPrefix);
        final CharMatcher matcher = CharMatcher.is(PERCENT_ENCODING);

        while (matcher.matchesAnyOf(parsedPrefix)) {
            final int percentCharPosition = matcher.indexIn(parsedPrefix);
            parsedPrefix.replace(percentCharPosition, percentCharPosition + LAST_ENCODED_CHAR,
                    String.valueOf((char) Integer.parseInt(parsedPrefix.substring(
                            percentCharPosition + FIRST_ENCODED_CHAR, percentCharPosition + LAST_ENCODED_CHAR),
                            PERCENT_ENCODED_RADIX)));
        }

        return parsedPrefix.toString();
    }

    private static Object toQName(final String value, final DataSchemaNode schemaNode,
            final SchemaContext schemaContext) {
        final String moduleName = toModuleName(value);
        final String nodeName = toNodeName(value);
        final Module module = schemaContext.findModules(moduleName).iterator().next();
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
}
