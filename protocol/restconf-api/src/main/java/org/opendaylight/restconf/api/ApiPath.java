/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

/**
 * Intermediate representation of a parsed {@code api-path} string as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.3.1">RFC section 3.5.3.1</a>. It models the path
 * as a series of {@link Step}s.
 */
@NonNullByDefault
public final class ApiPath implements Immutable {
    /**
     * A single step in an {@link ApiPath}.
     */
    public abstract static sealed class Step implements Immutable {
        private final @Nullable String module;
        private final Unqualified identifier;

        Step(final @Nullable String module, final String identifier) {
            this.identifier = verifyNotNull(UnresolvedQName.tryLocalName(identifier),
                "Unexpected invalid identifier %s", identifier);
            this.module = module;
        }

        public Unqualified identifier() {
            return identifier;
        }

        public @Nullable String module() {
            return module;
        }

        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(@Nullable Object obj);

        final boolean equals(final Step other) {
            return Objects.equals(module, other.module) && identifier.equals(other.identifier);
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
        public ApiIdentifier(final @Nullable String module, final String identifier) {
            super(module, identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(module(), identifier());
        }

        @Override
        public boolean equals(final @Nullable Object obj) {
            return this == obj || obj instanceof ApiIdentifier other && equals(other);
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
        public int hashCode() {
            return Objects.hash(module(), identifier(), keyValues);
        }

        @Override
        public boolean equals(final @Nullable Object obj) {
            return this == obj || obj instanceof ListInstance other && equals(other)
                && keyValues.equals(other.keyValues);
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper).add("keyValues", keyValues);
        }
    }

    private static final ApiPath EMPTY = new ApiPath(ImmutableList.of());

    private final ImmutableList<Step> steps;

    private ApiPath(final ImmutableList<Step> steps) {
        this.steps = requireNonNull(steps);
    }

    /**
     * Return an empty ApiPath.
     *
     * @return An empty ApiPath.
     */
    public static ApiPath empty() {
        return EMPTY;
    }

    /**
     * Parse an {@link ApiPath} from a raw Request URI fragment or another source. The string is expected to contain
     * percent-encoded bytes. Any sequence of such bytes is interpreted as a {@code UTF-8}-encoded string. Invalid
     * sequences are rejected.
     *
     * @param str Request URI part
     * @return An {@link ApiPath}
     * @throws NullPointerException if {@code str} is {@code null}
     * @throws ParseException if the string cannot be parsed
     */
    public static ApiPath parse(final String str) throws ParseException {
        return str.isEmpty() ? EMPTY : parseString(ApiPathParser.newStrict(), str);
    }

    /**
     * Parse an {@link ApiPath} from a raw Request URI fragment. The string is expected to contain percent-encoded
     * bytes. Any sequence of such bytes is interpreted as a {@code UTF-8}-encoded string. Invalid sequences are
     * rejected, but consecutive slashes may be tolerated, depending on runtime configuration.
     *
     * @param str Request URI part
     * @return An {@link ApiPath}
     * @throws NullPointerException if {@code str} is {@code null}
     * @throws ParseException if the string cannot be parsed
     */
    public static ApiPath parseUrl(final String str) throws ParseException {
        return str.isEmpty() ? EMPTY : parseString(ApiPathParser.newUrl(), str);
    }

    public ImmutableList<Step> steps() {
        return steps;
    }

    public int indexOf(final String module, final String identifier) {
        final var m = requireNonNull(module);
        final var id = requireNonNull(identifier);
        for (int i = 0, size = steps.size(); i < size; ++i) {
            final var step = steps.get(i);
            if (m.equals(step.module) && id.equals(step.identifier.getLocalName())) {
                return i;
            }
        }
        return -1;
    }

    public ApiPath subPath(final int fromIndex) {
        return subPath(fromIndex, steps.size());
    }

    public ApiPath subPath(final int fromIndex, final int toIndex) {
        final var subList = steps.subList(fromIndex, toIndex);
        if (subList == steps) {
            return this;
        } else if (subList.isEmpty()) {
            return EMPTY;
        } else {
            return new ApiPath(subList);
        }
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this || obj instanceof ApiPath other && steps.equals(other.steps());
    }

    private static ApiPath parseString(final ApiPathParser parser, final String str) throws ParseException {
        final var steps = parser.parseSteps(str);
        return steps.isEmpty() ? EMPTY : new ApiPath(steps);
    }
}
