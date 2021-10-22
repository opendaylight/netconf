/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import com.google.common.base.CharMatcher;
import org.opendaylight.yangtools.yang.common.YangNames;

/**
 * Various constants related to RFC3986 (URI) and RFC7950 (YANG) parsing in the context of RFC8040.
 */
final class ParserConstants {
    // Reserved characters as per https://tools.ietf.org/html/rfc3986#section-2.2
    static final String RFC3986_RESERVED_CHARACTERS = ":/?#[]@" + "!$&'()*+,;="
            // FIXME: this space should not be here, but that was a day-0 bug and we have asserts on this
            + " ";

    // Subsequent characters of RFC7950 "identifier" rule
    static final CharMatcher YANG_IDENTIFIER_PART = YangNames.NOT_IDENTIFIER_PART.negate().precomputed();

    private ParserConstants() {
        // Hidden on purpose
    }
}
