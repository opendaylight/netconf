/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
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
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class YangInstanceIdentifierDeserializer {
    private static final CharMatcher IDENTIFIER_PREDICATE =
            CharMatcher.noneOf(ParserConstants.RFC3986_RESERVED_CHARACTERS).precomputed();
    private static final String PARSING_FAILED_MESSAGE = "Could not parse Instance Identifier '%s'. "
            + "Offset: '%d' : Reason: ";
    private static final CharMatcher PERCENT_ENCODING = CharMatcher.is('%');
    // position of the first encoded char after percent sign in percent encoded string
    private static final int FIRST_ENCODED_CHAR = 1;
    // position of the last encoded char after percent sign in percent encoded string
    private static final int LAST_ENCODED_CHAR = 3;
    // percent encoded radix for parsing integers
    private static final int PERCENT_ENCODED_RADIX = 16;

    private final EffectiveModelContext schemaContext;
    private final String data;

    private DataSchemaContextNode<?> current;
    private int offset;

    private YangInstanceIdentifierDeserializer(final EffectiveModelContext schemaContext, final String data) {
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
    public static Iterable<PathArgument> create(final EffectiveModelContext schemaContext, final String data) {
        return new YangInstanceIdentifierDeserializer(schemaContext, data).parse();
    }

    private List<PathArgument> parse() {
        final List<PathArgument> path = new ArrayList<>();

        while (!allCharsConsumed()) {
            validArg();
            final QName qname = prepareQName();

            // this is the last identifier (input is consumed) or end of identifier (slash)
            if (allCharsConsumed() || currentChar() == '/') {
                prepareIdentifier(qname, path);
                path.add(current == null ? NodeIdentifier.create(qname) : current.getIdentifier());
            } else if (currentChar() == '=') {
                if (nextContextNode(qname, path).getDataSchemaNode() instanceof ListSchemaNode) {
                    prepareNodeWithPredicates(qname, path, (ListSchemaNode) current.getDataSchemaNode());
                } else {
                    prepareNodeWithValue(qname, path);
                }
            } else {
                throw getParsingCharFailedException();
            }
        }

        return ImmutableList.copyOf(path);
    }

    private void prepareNodeWithPredicates(final QName qname, final List<PathArgument> path,
            final ListSchemaNode listSchemaNode) {
        checkValid(listSchemaNode != null, ErrorTag.MALFORMED_MESSAGE, "Data schema node is null");

        final Iterator<QName> keys = listSchemaNode.getKeyDefinition().iterator();
        final ImmutableMap.Builder<QName, Object> values = ImmutableMap.builder();

        // skip already expected equal sign
        skipCurrentChar();

        // read key value separated by comma
        while (keys.hasNext() && !allCharsConsumed() && currentChar() != '/') {

            // empty key value
            if (currentChar() == ',') {
                values.put(keys.next(), "");
                skipCurrentChar();
                continue;
            }

            // check if next value is parsable
            checkValid(IDENTIFIER_PREDICATE.matches(currentChar()), ErrorTag.MALFORMED_MESSAGE,
                    "Value that starts with character %c is not parsable.", currentChar());

            // parse value
            final QName key = keys.next();
            Optional<DataSchemaNode> leafSchemaNode = listSchemaNode.findDataChildByName(key);
            RestconfDocumentedException.throwIf(!leafSchemaNode.isPresent(), ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
                    "Schema not found for %s", key);

            final String value = findAndParsePercentEncoded(nextIdentifierFromNextSequence(IDENTIFIER_PREDICATE));
            final Object valueByType = prepareValueByType(leafSchemaNode.get(), value);
            values.put(key, valueByType);

            // skip comma
            if (keys.hasNext() && !allCharsConsumed() && currentChar() == ',') {
                skipCurrentChar();
            }
        }

        // the last key is considered to be empty
        if (keys.hasNext()) {
            // at this point, it must be true that current char is '/' or all chars have already been consumed
            values.put(keys.next(), "");

            // there should be no more missing keys
            RestconfDocumentedException.throwIf(keys.hasNext(), ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                    "Cannot parse input identifier '%s'. Key value is missing for QName: %s", data, qname);
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
        final String preparedPrefix = nextIdentifierFromNextSequence(ParserConstants.YANG_IDENTIFIER_PART);
        final String prefix;
        final String localName;

        if (allCharsConsumed()) {
            return getQNameOfDataSchemaNode(preparedPrefix);
        }

        switch (currentChar()) {
            case '/':
            case '=':
                prefix = preparedPrefix;
                return getQNameOfDataSchemaNode(prefix);
            case ':':
                prefix = preparedPrefix;
                skipCurrentChar();
                checkValidIdentifierStart();
                localName = nextIdentifierFromNextSequence(ParserConstants.YANG_IDENTIFIER_PART);

                if (!allCharsConsumed() && currentChar() == '=') {
                    return getQNameOfDataSchemaNode(localName);
                } else {
                    final Module module = moduleForPrefix(prefix);
                    RestconfDocumentedException.throwIf(module == null, ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
                            "Failed to lookup for module with name '%s'.", prefix);
                    return QName.create(module.getQNameModule(), localName);
                }
            default:
                throw getParsingCharFailedException();
        }
    }

    private void prepareNodeWithValue(final QName qname, final List<PathArgument> path) {
        skipCurrentChar();
        final String value = nextIdentifierFromNextSequence(IDENTIFIER_PREDICATE);

        // exception if value attribute is missing
        RestconfDocumentedException.throwIf(value.isEmpty(), ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                "Cannot parse input identifier '%s' - value is missing for QName: %s.", data, qname);
        final DataSchemaNode dataSchemaNode = current.getDataSchemaNode();
        final Object valueByType = prepareValueByType(dataSchemaNode, findAndParsePercentEncoded(value));
        path.add(new YangInstanceIdentifier.NodeWithValue<>(qname, valueByType));
    }

    private void prepareIdentifier(final QName qname, final List<PathArgument> path) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname, path);
        if (currentNode != null) {
            checkValid(!currentNode.isKeyedEntry(), ErrorTag.MISSING_ATTRIBUTE,
                    "Entry '%s' requires key or value predicate to be present.", qname);
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
        checkValid(current != null, ErrorTag.MALFORMED_MESSAGE, "'%s' is not correct schema node identifier.", qname);
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

    private void checkValid(final boolean condition, final ErrorTag errorTag, final String errorMsg,
                            final Object... messageArgs) {
        final Object[] allMessageArguments = new Object[messageArgs.length + 2];
        allMessageArguments[0] = data;
        allMessageArguments[1] = offset;
        System.arraycopy(messageArgs, 0, allMessageArguments, 2, messageArgs.length);
        RestconfDocumentedException.throwIf(!condition, ErrorType.PROTOCOL, errorTag,
                PARSING_FAILED_MESSAGE + errorMsg, allMessageArguments);
    }

    private void checkValidIdentifierStart() {
        checkValid(ParserConstants.YANG_IDENTIFIER_START.matches(currentChar()), ErrorTag.MALFORMED_MESSAGE,
                "Identifier must start with character from set 'a-zA-Z_'");
    }

    private RestconfDocumentedException getParsingCharFailedException() {
        return new RestconfDocumentedException(String.format(PARSING_FAILED_MESSAGE, data, offset)
                + String.format("Bad char '%c' on the current position.", currentChar()),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
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
            checkValid('/' == currentChar(), ErrorTag.MALFORMED_MESSAGE, "Identifier must start with '/'.");

            // skip consecutive slashes, users often assume restconf URLs behave just as HTTP does by squashing
            // multiple slashes into a single one
            while (!allCharsConsumed() && '/' == currentChar()) {
                skipCurrentChar();
            }

            // check if slash is not also the last char in identifier
            checkValid(!allCharsConsumed(), ErrorTag.MALFORMED_MESSAGE, "Identifier cannot end with '/'.");
        }
    }

    private QName getQNameOfDataSchemaNode(final String nodeName) {
        final DataSchemaNode dataSchemaNode = current.getDataSchemaNode();
        if (dataSchemaNode instanceof ContainerLike) {
            return getQNameOfDataSchemaNode((ContainerLike) dataSchemaNode, nodeName);
        } else if (dataSchemaNode instanceof ListSchemaNode) {
            return getQNameOfDataSchemaNode((ListSchemaNode) dataSchemaNode, nodeName);
        }

        throw new UnsupportedOperationException("Unsupported schema node " + dataSchemaNode);
    }

    private static <T extends DataNodeContainer & SchemaNode & ActionNodeContainer> QName getQNameOfDataSchemaNode(
            final T parent, final String nodeName) {
        final Optional<? extends ActionDefinition> actionDef = findActionDefinition(parent, nodeName);
        final SchemaNode node;
        if (actionDef.isPresent()) {
            node = actionDef.get();
        } else {
            node = RestconfSchemaUtil.findSchemaNodeInCollection(parent.getChildNodes(), nodeName);
        }
        return node.getQName();
    }

    private static Optional<? extends ActionDefinition> findActionDefinition(final SchemaNode dataSchemaNode,
            final String nodeName) {
        requireNonNull(dataSchemaNode, "DataSchema Node must not be null.");
        if (dataSchemaNode instanceof ActionNodeContainer) {
            return ((ActionNodeContainer) dataSchemaNode).getActions().stream()
                    .filter(actionDef -> actionDef.getQName().getLocalName().equals(nodeName)).findFirst();
        }
        return Optional.empty();
    }

    private static String findAndParsePercentEncoded(final String preparedPrefix) {
        if (preparedPrefix.indexOf('%') == -1) {
            return preparedPrefix;
        }

        // FIXME: this is extremely inefficient: we should be converting ranges of characters, not driven by
        //        CharMatcher, but by String.indexOf()
        final StringBuilder parsedPrefix = new StringBuilder(preparedPrefix);
        while (PERCENT_ENCODING.matchesAnyOf(parsedPrefix)) {
            final int percentCharPosition = PERCENT_ENCODING.indexIn(parsedPrefix);
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
