/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.parser.builder;

import com.google.common.base.CharMatcher;
import org.opendaylight.restconf.parser.builder.YangInstanceIdentifierDeserializerBuilder;
import org.opendaylight.restconf.parser.builder.YangInstanceIdentifierSerializerBuilder;

/**
 * Util class of constants of {@link YangInstanceIdentifierSerializerBuilder}
 * and {@link YangInstanceIdentifierDeserializerBuilder}
 *
 */
public final class ParserBuilderConstants {

    private ParserBuilderConstants() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Constants for {@link YangInstanceIdentifierSerializerBuilder}
     *
     */
    public static final class Serializer {

        private Serializer() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String DISABLED_CHARS = ",': /";
        public static final CharMatcher PERCENT_ENCODE_CHARS = CharMatcher.anyOf(DISABLED_CHARS).precomputed();
    }

    /**
     * Constants for {@link YangInstanceIdentifierSerializerBuilder}
     *
     */
    public static final class Deserializer {

        private Deserializer() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final CharMatcher BASE = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
                .precomputed();
        public static final CharMatcher IDENTIFIER_FIRST_CHAR = BASE.or(CharMatcher.is('_')).precomputed();
        public static final CharMatcher IDENTIFIER = IDENTIFIER_FIRST_CHAR.or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.anyOf(".-")).precomputed();
        public static final CharMatcher IDENTIFIER_HEXA = CharMatcher.inRange('a', 'f')
                .or(CharMatcher.inRange('A', 'F')).or(CharMatcher.inRange('0', '9')).precomputed();

        public static final char COLON = ':';
        public static final char EQUAL = '=';
        public static final char COMMA = ',';
        public static final char PERCENT_ENCODING = '%';
        public static final char QUOTE = '"';

        public static final CharMatcher IDENTIFIER_FIRST_CHAR_PREDICATE = BASE.or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.is(QUOTE)).or(CharMatcher.is(PERCENT_ENCODING)).precomputed();

        public static final CharMatcher IDENTIFIER_PREDICATE = IDENTIFIER_FIRST_CHAR_PREDICATE;
        public static final String EMPTY_STRING = "";
    }
}