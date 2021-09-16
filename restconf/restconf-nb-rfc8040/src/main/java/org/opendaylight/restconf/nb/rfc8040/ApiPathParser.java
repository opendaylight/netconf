/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;

/**
 * Parser for a sequence of {@link ApiPath}'s {@link Step}s.
 */
@NonNullByDefault
final class ApiPathParser {
    // FIXME: use these from YangNames
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    private static final CharMatcher NOT_IDENTIFIER_PART =
        IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).negate().precomputed();

    private ApiPathParser() {
        // Hidden on purpose
    }

    /**
     * Parse an {@link ApiPath}'s steps from a raw Request URI.
     *
     * @param str Request URI, with leading {@code {+restconf}} stripped.
     * @return A list of steps
     * @throws ParseException if the string cannot be parsed
     * @throws NullPointerException if {@code str} is {@code null}
     */
    static ImmutableList<Step> parseSteps(final String str) throws ParseException {
        // We are dealing with a raw request URI, which may contain percent-encoded spans. Dealing with them during
        // structural parsing is rather annoying, especially since they can be multi-byte sequences. Let's make a quick
        // check if there are any percent-encoded bytes and take the appropriate fast path.
        final int firstPercent = str.indexOf('%');
        return firstPercent == -1 ? parseDecoded(str, true) : parseEncoded(str, firstPercent);
    }

    private static ImmutableList<Step> parseEncoded(final String str, final int firstPercent)
            throws ParseException {
        //        Since UTF-8 does not allow 0x00-0x7F to occur anywhere but the first byte, we can safely
        //        percent-decode everything except '/' (%47), ',' (%44), ':' (%58) and '=' (%61). That peels all the
        //        multi-byte decoding from the problem at hand.
        final int limit = str.length();
        final StringBuilder sb = new StringBuilder(limit);
        final Utf8Buffer decoder = new Utf8Buffer();

        boolean noPercent = true;
        int nextPercent = firstPercent;
        int idx = 0;
        do {
            sb.append(str, idx, nextPercent);
            idx = nextPercent;

            final int b = parsePercent(str, idx, limit);
            final int nextIdx = idx + 3;
            if (b == '/' || b == ',') {
                decoder.flushTo(sb, idx);
                sb.append(str, idx, nextIdx);
                noPercent = false;
            } else {
                decoder.appendByte(b);
                // Lookahead: if the next is not an escape, we need to flush the buffer in preparation
                //            for the bulk append at the top of the loop.
                if (nextIdx != limit && str.charAt(nextIdx)  != '%') {
                    decoder.flushTo(sb, idx);
                }
            }

            nextPercent = str.indexOf('%', nextIdx);
            idx = nextIdx;
        } while (nextPercent != -1);

        decoder.flushTo(sb, idx);
        return parseDecoded(sb.append(str, idx, limit).toString(), noPercent);
    }

    private static ImmutableList<Step> parseDecoded(final String str, final boolean noPercent) throws ParseException {
        final var steps = ImmutableList.<Step>builder();
        int idx = 0;
        do {
            // FIXME: deal with percents
            final int slash = str.indexOf('/', idx);
            if (slash == idx) {
                throw new ParseException("Unexpected '/'", idx);
            }

            final int limit = slash != -1 ? slash : str.length();
            steps.add(parseStep(str, idx, limit, noPercent));
            idx = limit + 1;
        } while (idx < str.length());

        return steps.build();
    }

    private static Step parseStep(final String str, final int offset, final int limit, final boolean noPercent)
            throws ParseException {
        // Mandatory first identifier
        final String first = parseIdentifier(str, offset, limit);
        int idx = offset + first.length();
        if (idx == limit) {
            return new ApiIdentifier(null, first);
        }

        // Optional second identifier
        final String second;
        if (str.charAt(idx) == ':') {
            second = parseIdentifier(str, ++idx, limit);
            idx += second.length();

            if (idx == limit) {
                return new ApiIdentifier(first, second);
            }
        } else {
            second = null;
        }

        // Key values
        if (str.charAt(idx) != '=') {
            throw new ParseException("Expecting '='", idx);
        }
        final var keyValues = parseKeyValues(str, idx + 1, limit, noPercent);
        return second != null ? new ListInstance(first, second, keyValues) : new ListInstance(null, first, keyValues);
    }

    private static String parseIdentifier(final String str, final int offset, final int limit)
            throws ParseException {
        if (!IDENTIFIER_START.matches(str.charAt(offset))) {
            throw new ParseException("Expecting [a-zA-Z_]", offset);
        }

        final int nonMatch = NOT_IDENTIFIER_PART.indexIn(str, offset + 1);
        return str.substring(offset, nonMatch != -1 && nonMatch < limit ? nonMatch : limit);
    }

    private static ImmutableList<String> parseKeyValues(final String str, final int offset, final int limit,
            final boolean noPercent) throws ParseException {
        final var values = ImmutableList.<String>builder();
        int idx = offset;
        while (true) {
            // FIXME: deal with percents
            final int comma = str.indexOf(',', idx);
            if (comma == -1 || comma > limit) {
                values.add(decodeValue(str, idx, limit, noPercent));
                return values.build();
            }

            values.add(decodeValue(str, idx, comma, noPercent));
            idx = comma + 1;
        }
    }

    private static String decodeValue(final String str, final int offset, final int limit, final boolean noPercent)
            throws ParseException {
        return noPercent ? str.substring(offset, limit) : unescapeValue(str, offset, limit);
    }

    private static String unescapeValue(final String str, final int offset, final int limit) throws ParseException {
        int percent = str.indexOf('%', offset);
        if (percent == -1 || percent > limit) {
            return str.substring(offset, limit);
        }

        final StringBuilder sb = new StringBuilder(limit - offset);
        int idx = offset;
        while (true) {
            sb.append(str, idx, percent);
            idx = percent;

            // We have taken care of almost all escapes except for those we need for framing at this point
            final int b = parsePercent(str, idx, limit);
            if (b == '/' || b == ',') {
                sb.append((char) b);
            } else {
                throw new ParseException("Unexpected byte '" + b + "'", idx);
            }

            idx += 3;
            percent = str.indexOf('%', idx);
            if (percent == -1 || percent >= limit) {
                return sb.append(str, idx, limit).toString();
            }
        }
    }

    private static int parsePercent(final String str, final int offset, final int limit) throws ParseException {
        if (limit - offset < 3) {
            throw new ParseException("Incomplete escape '" + str.substring(offset) + "'", offset);
        }
        return (byte) (parseHex(str, offset + 1) << 4 | parseHex(str, offset + 2));
    }

    private static int parseHex(final String str, final int offset) throws ParseException {
        final char ch = str.charAt(offset);
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }

        final int zero;
        if (ch >= 'a' && ch <= 'f') {
            zero = 'a';
        } else if (ch >= 'A' && ch <= 'F') {
            zero = 'A';
        } else {
            throw new ParseException("Invalid escape character '" + ch + "'", offset);
        }

        return ch - zero + 10;
    }
}
