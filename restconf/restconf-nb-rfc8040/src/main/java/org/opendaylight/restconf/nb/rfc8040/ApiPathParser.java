/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;

/**
 * Parser for a sequence of {@link ApiPath}'s {@link Step}s.
 */
final class ApiPathParser {
    // FIXME: use these from YangNames
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    private static final CharMatcher NOT_IDENTIFIER_PART =
        IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).negate().precomputed();

    private final Builder<Step> steps = ImmutableList.builder();
    private final String src;

    // the offset of the character returned from last peekBasicLatin()
    private int nextOffset;

    /*
     * State tracking for creating substrings:
     *
     * Usually we copy spans 'src', in which case subStart captures 'start' argument to String.substring(...).
     * If we encounter a percent escape we need to interpret as part of the string, we start building the string in
     * subBuilder -- in which case subStart is set to -1.
     *
     * Note that StringBuilder is lazily-instantiated, as we have no percents at all
     */
    private int subStart;
    private StringBuilder subBuilder;

    // Lazily-allocated when we need to decode UTF-8. Since we touch this only when we are not expecting
    private Utf8Buffer buf;

    private ApiPathParser(final String src) {
        this.src = requireNonNull(src);
    }

    static @NonNull ImmutableList<Step> parseSteps(final String str) throws ParseException {
        return new ApiPathParser(str).parse();
    }

    // Grammar:
    //   steps : step ("/" step)*
    private @NonNull ImmutableList<Step> parse() throws ParseException {
        int idx = 0;
        do {
            final int slash = src.indexOf('/', idx);
            final int limit = slash != -1 ? slash : src.length();
            final int next = parseStep(idx, limit);
            verify(next == limit, "Unconsumed bytes: %s next %s limit", next, limit);
            idx = next + 1;
        } while (idx < src.length());

        return steps.build();
    }

    // Grammar:
    //   step : identifier (":" identifier)? ("=" key-value ("," key-value)*)?
    private int parseStep(final int offset, final int limit) throws ParseException {
        int idx = startIdentifier(offset, limit);
        while (idx < limit) {
            final char ch = peekBasicLatin(idx, limit);
            if (ch == ':') {
                return parseStep(endSub(idx), nextOffset, limit);
            } else if (ch == '=') {
                return parseStep(null, endSub(idx), nextOffset, limit);
            }
            idx = continueIdentifer(idx, ch);
        }

        steps.add(new ApiIdentifier(null, endSub(idx)));
        return idx;
    }

    // Starting at second identifier
    private int parseStep(final @Nullable String module, final int offset, final int limit) throws ParseException {
        int idx = startIdentifier(offset, limit);
        while (idx < limit) {
            final char ch = peekBasicLatin(idx, limit);
            if (ch == '=') {
                return parseStep(module, endSub(idx), nextOffset, limit);
            }
            idx = continueIdentifer(idx, ch);
        }

        steps.add(new ApiIdentifier(module, endSub(idx)));
        return idx;
    }

    // Starting at first key-value
    private int parseStep(final @Nullable String module, final @NonNull String identifier, final int offset,
            final int limit) throws ParseException {
        final var values = ImmutableList.<String>builder();

        startSub(offset);
        int idx = offset;
        while (idx < limit) {
            final char ch = src.charAt(idx);
            if (ch == ',') {
                values.add(endSub(idx));
                startSub(++idx);
            } else if (ch != '%') {
                append(ch);
                idx++;
            } else {
                // Save current string content and capture current index for reporting
                final var sb = flushSub(idx);
                final int errorOffset = idx;

                var utf = buf;
                if (utf == null) {
                    buf = utf = new Utf8Buffer();
                }

                do {
                    utf.appendByte(parsePercent(src, idx, limit));
                    idx += 3;
                } while (idx < limit && src.charAt(idx) == '%');

                utf.flushTo(sb, errorOffset);
            }
        }

        steps.add(new ListInstance(module, identifier, values.add(endSub(idx)).build()));
        return idx;
    }

    private int startIdentifier(final int offset, final int limit) throws ParseException {
        startSub(offset);
        final char ch = peekBasicLatin(offset, limit);
        if (!IDENTIFIER_START.matches(ch)) {
            throw new ParseException("Expecting [a-zA-Z_], not '" + ch + "'", offset);
        }
        append(ch);
        return nextOffset;
    }

    private int continueIdentifer(final int offset, final char ch) throws ParseException {
        if (NOT_IDENTIFIER_PART.matches(ch)) {
            throw new ParseException("Expecting [a-zA-Z_.-], not '" + ch + "'", offset);
        }
        append(ch);
        return nextOffset;
    }

    // Assert current character comes from the Basic Latin block, i.e. 00-7F.
    // Callers are expected to pick up 'nextIdx' to resume parsing at the next character
    private char peekBasicLatin(final int offset, final int limit) throws ParseException {
        final char ch = src.charAt(offset);
        if (ch == '%') {
            final byte b = parsePercent(src, offset, limit);
            if (b < 0) {
                throw new ParseException("Expecting %00-%7F, not " + src.substring(offset, limit), offset);
            }

            flushSub(offset);
            nextOffset = offset + 3;
            return (char) b;
        }

        if (ch < 0 || ch > 127) {
            throw new ParseException("Unexpected character '" + ch + "'", offset);
        }
        nextOffset = offset + 1;
        return ch;
    }

    private void startSub(final int offset) {
        subStart = offset;
    }

    private void append(final char ch) {
        // We are not reusing string, append the char, otherwise
        if (subStart == -1) {
            verifyNotNull(subBuilder).append(ch);
        }
    }

    private @NonNull String endSub(final int end) {
        return subStart != -1 ? src.substring(subStart, end) : verifyNotNull(subBuilder).toString();
    }

    private @NonNull StringBuilder flushSub(final int end) {
        var sb = subBuilder;
        if (sb == null) {
            subBuilder = sb = new StringBuilder();
        }
        if (subStart != -1) {
            sb.setLength(0);
            sb.append(src, subStart, end);
            subStart = -1;
        }
        return sb;
    }

    private static byte parsePercent(final String str, final int offset, final int limit) throws ParseException {
        if (limit - offset < 3) {
            throw new ParseException("Incomplete escape '" + str.substring(offset, limit) + "'", offset);
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
