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
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.concepts.Builder;
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

public class YangInstanceIdentifierDeserializerBuilder implements Builder<Iterable<PathArgument>> {

    private static final CharMatcher BASE = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
            .precomputed();
    private static final CharMatcher IDENTIFIER_FIRST_CHAR = BASE.or(CharMatcher.is('_')).precomputed();
    private static final CharMatcher IDENTIFIER = IDENTIFIER_FIRST_CHAR.or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf(".-")).precomputed();
    private static final CharMatcher IDENTIFIER_HEXA = CharMatcher.inRange('a', 'f').or(CharMatcher.inRange('A', 'F'))
            .or(CharMatcher.inRange('0', '9')).precomputed();

    private static final char COLON = ':';
    private static final char EQUAL = '=';
    private static final char COMMA = ',';
    private static final char PERCENT_ENCODING = '%';
    private static final char QUOTE = '"';

    private static final CharMatcher IDENTIFIER_FIRST_CHAR_PREDICATE = BASE.or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.is(QUOTE)).or(CharMatcher.is(PERCENT_ENCODING)).precomputed();

    private static final CharMatcher IDENTIFIER_PREDICATE = IDENTIFIER_FIRST_CHAR_PREDICATE;
    private static final String EMPTY_STRING = "";

    private final List<PathArgument> path = new LinkedList<>();

    private final String data;
    private final SchemaContext schemaContext;

    private int offset;
    private DataSchemaContextNode<?> current;

    public YangInstanceIdentifierDeserializerBuilder(final SchemaContext schemaContext, final String data) {
        this.schemaContext = schemaContext;
        this.current = DataSchemaContextTree.from(schemaContext).getRoot();
        this.data = data;
        this.offset = 0;
    }

    @Override
    public Iterable<PathArgument> build() {
        while (!allCharsConsumed()) {
            this.path.add(prepareNextArg());
        }
        return ImmutableList.copyOf(this.path);
    }

    private PathArgument prepareNextArg() {
        validArg();
        final QName qname = nextQName();
        if (!allCharsConsumed() && (currentChar() == RestconfConstants.SLASH)) {
            return prepareIdentifier(qname);
        } else if (!allCharsConsumed() && (currentChar() == EQUAL)) {
            return prepareIdentifierWithPredicate(qname);
        }
        throw new IllegalArgumentException("Bad char " + currentChar() + " on position " + this.offset + ".");
    }

    private PathArgument prepareNodeWithValue(final QName qname) {
        skipCurrentChar();
        final String value = nextIdentifier();
        return new YangInstanceIdentifier.NodeWithValue<>(qname, value);
    }

    private PathArgument prepareIdentifierWithPredicate(final QName qname) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname);
        if (!currentNode.isKeyedEntry()) {
            return prepareNodeWithValue(qname);
        }
        final List<QName> keyDefinitions = ((ListSchemaNode) currentNode.getDataSchemaNode()).getKeyDefinition();
        final ImmutableMap.Builder<QName, Object> keyValues = ImmutableMap.builder();

        for (final QName keyQName : keyDefinitions) {
            skipCurrentChar();
            final String predicate = nextPredicate();
            keyValues.put(keyQName, predicate);
        }
        return new YangInstanceIdentifier.NodeIdentifierWithPredicates(qname, keyValues.build());
    }

    private String nextPredicate() {
        final String valueOfKey = nextIdentifierPredicate();
        return findAndParsePercentEncoded(valueOfKey);
    }

    private String nextIdentifierPredicate() {
        if ((currentChar() == COMMA) || (currentChar() == RestconfConstants.SLASH)) {
            return EMPTY_STRING;
        }
        final int start = this.offset;
        checkValid(IDENTIFIER_FIRST_CHAR_PREDICATE.matches(currentChar()),
                "Identifier must start with character from set 'a-zA-Z_'");
        nextSequenceEnd(IDENTIFIER_PREDICATE);
        return this.data.substring(start, this.offset);
    }

    private PathArgument prepareIdentifier(final QName qname) {
        final DataSchemaContextNode<?> currentNode = nextContextNode(qname);
        checkValid(!currentNode.isKeyedEntry(), "Entry " + qname + " requires key or value predicate to be present");
        return currentNode.getIdentifier();
    }

    private DataSchemaContextNode<?> nextContextNode(final QName qname) {
        this.current = this.current.getChild(qname);
        checkValid(this.current != null, qname + " is not correct schema node identifier.");
        while (this.current.isMixin()) {
            this.path.add(this.current.getIdentifier());
            this.current = this.current.getChild(qname);
        }
        return this.current;
    }

    private QName nextQName() {
        final String preparedPrefix = nextIdentifier();
        final String prefix, localName;
        switch (currentChar()) {
            case COLON:
                prefix = preparedPrefix;
                skipCurrentChar();
                localName = nextIdentifier();
                final Module module = moduleForPrefix(prefix);
                Preconditions.checkArgument(module != null, "Failed to lookup prefix %s", prefix);
                return QName.create(module.getQNameModule(), localName);
            case EQUAL:
                prefix = preparedPrefix;
                return getQNameOfDataSchemaNode(prefix);
            default:
                throw new IllegalArgumentException("Failed build path.");
        }
    }

    private String findAndParsePercentEncoded(String preparedPrefix) {
        if (!preparedPrefix.contains(String.valueOf(PERCENT_ENCODING))) {
            return preparedPrefix;
        }
        final StringBuilder newPrefix = new StringBuilder();
        int i = 0;
        int startPoint = 0;
        int endPoint = preparedPrefix.length();
        while ((i = preparedPrefix.indexOf(PERCENT_ENCODING)) != -1) {
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

    private QName getQNameOfDataSchemaNode(final String nodeName) {
        final DataSchemaNode dataSchemaNode = this.current.getDataSchemaNode();
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

    private Module moduleForPrefix(final String prefix) {
        return this.schemaContext.findModuleByName(prefix, null);
    }

    private String nextIdentifier() {
        final int start = this.offset;
        checkValid(IDENTIFIER_FIRST_CHAR.matches(currentChar()),
                "Identifier must start with character from set 'a-zA-Z_'");
        nextSequenceEnd(IDENTIFIER);
        return this.data.substring(start, this.offset);
    }

    private void nextSequenceEnd(final CharMatcher matcher) {
        while (!allCharsConsumed() && matcher.matches(this.data.charAt(this.offset))) {
            this.offset++;
        }
    }

    private void validArg() {
        checkValid(RestconfConstants.SLASH == currentChar(), "Identifier must start with '/'.");
        skipCurrentChar();
        checkValid(!allCharsConsumed(), "Identifier cannot end with '/'.");
    }

    private void skipCurrentChar() {
        this.offset++;
    }

    private char currentChar() {
        return this.data.charAt(this.offset);
    }

    private void checkValid(final boolean condition, final String errorMsg) {
        Preconditions.checkArgument(condition, "Could not parse Instance Identifier '%s'. Offset: %s : Reason: %s",
                this.data, this.offset, errorMsg);
    }

    private boolean allCharsConsumed() {
        return this.offset == this.data.length();
    }

}
