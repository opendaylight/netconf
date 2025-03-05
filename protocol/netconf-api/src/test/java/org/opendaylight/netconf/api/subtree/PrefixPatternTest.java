/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PrefixPatternTest {
    @ParameterizedTest
    @MethodSource
    void testExamples(final String prefix, final int index) {
        assertEquals(prefix, Prefixes.prefixOf(index));
    }

    private static List<Arguments> testExamples() {
        // 0 to 25 is one letter
        return List.of(Arguments.of("a", 0),
            Arguments.of("b", 1),
            Arguments.of("c", 2),
            Arguments.of("d", 3),
            Arguments.of("e", 4),
            Arguments.of("f", 5),
            Arguments.of("g", 6),
            Arguments.of("h", 7),
            Arguments.of("i", 8),
            Arguments.of("j", 9),
            Arguments.of("k", 10),
            Arguments.of("l", 11),
            Arguments.of("m", 12),
            Arguments.of("n", 13),
            Arguments.of("o", 14),
            Arguments.of("p", 15),
            Arguments.of("q", 16),
            Arguments.of("r", 17),
            Arguments.of("s", 18),
            Arguments.of("t", 19),
            Arguments.of("u", 20),
            Arguments.of("v", 21),
            Arguments.of("w", 22),
            Arguments.of("x", 23),
            Arguments.of("y", 24),
            Arguments.of("z", 25),
            // 26 total number of letters; on every 27th we repeat alphabet again
            Arguments.of("aa", 26),
            Arguments.of("ab", 27),
            Arguments.of("ac", 28),
            Arguments.of("ad", 29),
            Arguments.of("ae", 30),
            Arguments.of("af", 31),
            Arguments.of("ag", 32),
            Arguments.of("ah", 33),
            // 26 * 2 - 1 = 51 - this is alphabet twice and -1 because we start from 0
            Arguments.of("az", 26 * 2 - 1),
            Arguments.of("ba", 26 * 2),
            Arguments.of("bb", 26 * 2 + 1),
            Arguments.of("bc", 26 * 2 + 2),
            Arguments.of("bd", 26 * 2 + 3),
            // 26 to (26 * (26 + 1)) double letters; +1 because we account for "one letters" before
            // 26 * 27 - 1 - should be the moment we ran out of double letters and got for triple
            Arguments.of("zz", 26 * 27 - 1),
            Arguments.of("aaa", 26 * 27),
            Arguments.of("aab", 26 * 27 + 1),
            // At index 16573 generated prefix will be "xml" but we account for it, so method will take next prefix
            Arguments.of("xml", 26 * 27 + (26 * 26 * 23) + 26 * 12 + 11),
            Arguments.of("xmm", 26 * 27 + (26 * 26 * 23) + 26 * 12 + 12));
    }
}
