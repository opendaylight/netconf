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
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.UnqualifiedQName;

/**
 * Intermediate representation of a parsed 'api-path' string as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.5.3.1">RFC section 3.5.3.1</a>.
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
     * A single step in an {@link ApiPath}.
     */
    public static final class ApiIdentifier extends Step {
        ApiIdentifier(final @Nullable String module, final String identifier) {
            super(module, identifier);
        }
    }

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
     * @throws ParseException when the
     * @throws {@link NullPointerException} if {@code str} is {@code null}
     */
    public static ApiPath parse(final String str) throws ParseException {
        if (str.isEmpty()) {
            return EMPTY;
        }

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

    private static String unescapeValue(final String str, final int offset, final int limit) {
        final int percent = str.indexOf('%', offset);
        if (percent == -1 || percent > limit) {
            return str.substring(offset, limit);
        }

        throw new UnsupportedOperationException("Not implemented yet");
    }
}
