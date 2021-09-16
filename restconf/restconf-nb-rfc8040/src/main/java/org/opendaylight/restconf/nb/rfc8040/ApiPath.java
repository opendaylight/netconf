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
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.UnqualifiedQName;

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
    public abstract static class Step implements Immutable {
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
    public static final class ApiIdentifier extends Step {
        ApiIdentifier(final @Nullable String module, final String identifier) {
            super(module, identifier);
        }
    }

    /**
     * A {@code list-instance} step in a {@link ApiPath}.
     */
    public static final class ListInstance extends Step {
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
     * @throws NullPointerException if {@code str} is {@code null}
     */
    public static ApiPath parse(final String str) throws ParseException {
        if (str.isEmpty()) {
            return EMPTY;
        }

        // FIXME: we currently assume anything that does not need to be percent-encoded is not encoded. That is a
        //        rather huge assumption. Unfortunately that is not always the case and we need to be much smarter
        //        about this. We are dealing with UTF-8 encoding only, hence we can make other assumptions.
        //
        //        Since UTF-8 does not allow 0x00-0x7F to occur anywhere but the first byte, we can safely
        //        percent-decode '/' (%47), ',' (%44), ':' (%58) and '=' (%61) plus everything that is valid in
        //        'identifier' -- and can deal with the rest during key-value parsing.
        //
        //        That has a nice benefit of knowing when we explicitly do not need to deal with percent-encodiing,
        //        which is the usual case and should be an explicit fast path.

        final var steps = ImmutableList.<Step>builder();
        int idx = 0;
        do {
            final int slash = str.indexOf('/', idx);
            if (slash == idx) {
                throw new ParseException("Unexpected '/'", idx);
            }

            final int limit = slash != -1 ? slash : str.length();
            steps.add(parseStep(str, idx, limit));
            idx = limit + 1;
        } while (idx < str.length());

        return new ApiPath(steps.build());
    }

    public ImmutableList<Step> steps() {
        return steps;
    }

    private static Step parseStep(final String str, final int offset, final int limit) throws ParseException {
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
        final var keyValues = parseKeyValues(str, idx + 1, limit);
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

    private static ImmutableList<String> parseKeyValues(final String str, final int offset, final int limit)
            throws ParseException {
        final var values = ImmutableList.<String>builder();
        int idx = offset;
        while (true) {
            final int comma = str.indexOf(',', idx);
            if (comma == -1 || comma > limit) {
                values.add(unescapeValue(str, idx, limit));
                return values.build();
            }

            values.add(unescapeValue(str, idx, comma));
            idx = comma + 1;
        }
    }

    private static String unescapeValue(final String str, final int offset, final int limit) throws ParseException {
        int percent = str.indexOf('%', offset);
        if (percent == -1 || percent > limit) {
            return str.substring(offset, limit);
        }

        final StringBuilder sb = new StringBuilder(limit - offset);
        final ByteArrayOutputStream buf = new ByteArrayOutputStream(8);
        int idx = offset;
        while (true) {
            sb.append(str, idx, percent);
            idx = percent;

            do {
                if (limit - ++idx < 2) {
                    throw new ParseException("Incomplete escape", idx);
                }

                buf.write((byte) (decodeEscape(str, idx) << 4 | decodeEscape(str, idx + 1)));
                idx += 2;
            } while (idx < limit && str.charAt(idx) == '%');

            sb.append(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            buf.reset();

            percent = str.indexOf('%', idx);
            if (percent == -1 || percent >= limit) {
                return sb.append(str, idx, limit).toString();
            }
        }
    }

    private static int decodeEscape(final String str, final int offset) throws ParseException {
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
            throw new ParseException("Invalid escape character", offset);
        }

        return ch - zero + 10;
    }
}
