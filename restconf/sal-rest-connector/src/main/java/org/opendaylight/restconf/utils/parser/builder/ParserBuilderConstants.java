/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.parser.builder;

import com.google.common.base.CharMatcher;
import java.util.Arrays;
import org.opendaylight.restconf.parser.builder.YangInstanceIdentifierDeserializer;
import org.opendaylight.restconf.parser.builder.YangInstanceIdentifierSerializer;

/**
 * Util class of constants of {@link YangInstanceIdentifierSerializer}
 * and {@link YangInstanceIdentifierDeserializer}.
 *
 */
public final class ParserBuilderConstants {

    private ParserBuilderConstants() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Constants for {@link YangInstanceIdentifierSerializer}.
     *
     */
    public static final class Serializer {

        private Serializer() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String DISABLED_CHARS = Arrays.toString(new char[] { ':', '/', '?', '#', '[', ']', '@' })
                .concat(Arrays.toString(new char[] { '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' }));

        public static final CharMatcher PERCENT_ENCODE_CHARS = CharMatcher.anyOf(DISABLED_CHARS).precomputed();
    }

    /**
     * Constants for {@link YangInstanceIdentifierSerializer}.
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
        public static final char HYPHEN = '-';
        public static final char PERCENT_ENCODING = '%';

        public static final CharMatcher IDENTIFIER_PREDICATE = CharMatcher.noneOf(
                Serializer.DISABLED_CHARS).precomputed();

        public static final String EMPTY_STRING = "";

        // position of the first encoded char after percent sign in percent encoded string
        public static final int FIRST_ENCODED_CHAR = 1;
        // position of the last encoded char after percent sign in percent encoded string
        public static final int LAST_ENCODED_CHAR = 3;
        // percent encoded radix for parsing integers
        public static final int PERCENT_ENCODED_RADIX = 16;
    }
}