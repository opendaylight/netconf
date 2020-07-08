/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import com.google.common.base.CharMatcher;

/**
 * Various constants related to RFC3986 (URI) and RFC7950 (YANG) parsing in the context of RFC8040.
 */
final class ParserConstants {
    // Reserved characters as per https://tools.ietf.org/html/rfc3986#section-2.2
    // FIXME: note this string additionally contains a single space, as that is what the constant contained originally
    static final String RFC3986_RESERVED_CHARACTERS = ":/?#[]@" + "!$&'()*+,;= ";

    // First character of RFC7950 "identifier" rule
    static final CharMatcher YANG_IDENTIFIER_START =
            CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    // Subsequent characters of RFC7950 "identifier" rule
    static final CharMatcher YANG_IDENTIFIER_PART =
            YANG_IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).precomputed();

    private ParserConstants() {
        // Hidden on purpose
    }
}
