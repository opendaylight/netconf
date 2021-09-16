/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.UnqualifiedQName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intermediate representation of a parsed {@code api-path} string as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.5.3.1">RFC section 3.5.3.1</a>. It models the
 * path as a series of {@link Step}s.
 */
@Beta
@NonNullByDefault
public final class ApiPath implements Immutable {
    /**
     * A single step in an {@link ApiPath}.
     */
    abstract static class Step implements Immutable {
        private final @Nullable String module;
        private final UnqualifiedQName identifier;

        Step(final @Nullable String module, final String identifier) {
            this.identifier = verifyNotNull(UnqualifiedQName.tryCreate(identifier), "Unexpected invalid identifier %s",
                identifier);
            this.module = module;
        }

        public UnqualifiedQName identifier() {
            return identifier;
        }

        public @Nullable String module() {
            return module;
        }

        @Override
        public final String toString() {
            return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
        }

        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper.add("module", module).add("identifier", identifier);
        }
    }

    /**
     * An {@code api-identifier} step in a {@link ApiPath}.
     */
    static final class ApiIdentifier extends Step {
        ApiIdentifier(final @Nullable String module, final String identifier) {
            super(module, identifier);
        }
    }

    /**
     * A {@code list-instance} step in a {@link ApiPath}.
     */
    static final class ListInstance extends Step {
        private final ImmutableList<String> keyValues;

        ListInstance(final @Nullable String module, final String identifier, final ImmutableList<String> keyValues) {
            super(module, identifier);
            this.keyValues = requireNonNull(keyValues);
        }

        public ImmutableList<String> keyValues() {
            return keyValues;
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper.add("keyValues", keyValues);
        }
    }

    private static final ApiPath EMPTY = new ApiPath(ImmutableList.of());

    // FIXME: use these from YangNames
    private static final CharMatcher IDENTIFIER_START =
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z').or(CharMatcher.is('_'))).precomputed();
    private static final CharMatcher NOT_IDENTIFIER_PART =
        IDENTIFIER_START.or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("-.")).negate().precomputed();

    private final ImmutableList<Step> steps;

    private ApiPath(final ImmutableList<Step> steps) {
        this.steps = requireNonNull(steps);
    }

    /**
     * Parse an {@link ApiPath} from a raw Request URI.
     *
     * @param str Request URI, with leading {@code {+restconf}} stripped.
     * @return An {@link ApiPath}
     * @throws ParseException if the string cannot be parsed
     * @throws CharacterCodingException if the string contains invalid characters
     * @throws NullPointerException if {@code str} is {@code null}
     */
    public static ApiPath parse(final String str) throws ParseException {
        if (str.isEmpty()) {
            return EMPTY;
        }

        // We are dealing with a raw request URI, which may contain percent-encoded spans. Dealing with them during
        // structural parsing is rather annoying, especially since they can be multi-byte sequences. Let's make a quick
        // check if there are any percent-encoded bytes and take the appropriate fast path.
        final int firstPercent = str.indexOf('%');
        return new ApiPath(firstPercent == -1 ? parseDecoded(str, true) : parseEncoded(str, firstPercent));
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

    @VisibleForTesting
    ImmutableList<Step> steps() {
        return steps;
    }

    // Lazily-instantiated buffer, which collects bytes and can be asked to interpret them as a UTF-8 sequence,
    // appending the result into a StringBuilder
    private static final class Utf8Buffer {
        private static final Logger LOG = LoggerFactory.getLogger(Utf8Buffer.class);

        private @Nullable ByteArrayOutputStream bos;
        private @Nullable CharsetDecoder decoder;

        void appendByte(final int b) {
            var buf = bos;
            if (buf == null) {
                bos = buf = new ByteArrayOutputStream();
            }
            buf.write(b);
        }

        void flushTo(final StringBuilder sb, final int offset) throws ParseException {
            final var buf = bos;
            if (buf != null && buf.size() != 0) {
                flushBuf(sb, buf, offset);
            }
        }

        // Split out to aid inlinings
        private void flushBuf(final StringBuilder sb, final ByteArrayOutputStream buf, final int offset)
                throws ParseException {
            final var bytes = buf.toByteArray();
            buf.reset();

            // Special case for a single ASCII character, side-steps decoder/bytebuf allocation
            if (bytes.length == 1) {
                final byte b = bytes[0];
                if (b >= 0) {
                    sb.append((char) b);
                    return;
                }
            }
            try {
                append(sb, ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                LOG.trace("Encountered {} illegal bytes", bytes.length, e);
                throw new ParseException(
                    "Invalid UTF-8 sequence '" + new String(bytes, StandardCharsets.UTF_8) + "': " + e.getMessage(),
                    offset);
            }
        }

        private void append(final StringBuilder sb, final ByteBuffer bytes) throws CharacterCodingException {
            var local = decoder;
            if (local == null) {
                decoder = local = createDecoder();
            }
            sb.append(local.decode(bytes));
        }

        // Split out to silence Eclipse
        private static CharsetDecoder createDecoder() {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }
}
