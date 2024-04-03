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
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.text.ParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.HierarchicalIdentifier;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

/**
 * Intermediate representation of a parsed {@code api-path} string as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.3.1">RFC section 3.5.3.1</a>. It models the path
 * as a series of {@link Step}s.
 */
@NonNullByDefault
public record ApiPath(ImmutableList<Step> steps) implements HierarchicalIdentifier<ApiPath> {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * A single step in an {@link ApiPath}.
     */
    public abstract static sealed class Step implements Immutable {
        private final @Nullable String module;
        private final Unqualified identifier;

        @Deprecated(since = "7.0.4", forRemoval = true)
        Step(final @Nullable String module, final String identifier) {
            this.identifier = verifyNotNull(UnresolvedQName.tryLocalName(identifier),
                "Unexpected invalid identifier %s", identifier);
            this.module = module;
        }

        Step(final @Nullable String module, final Unqualified identifier) {
            this.module = module;
            this.identifier = requireNonNull(identifier);
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

        void appendTo(final StringBuilder sb) {
            appendTo(sb, module, identifier);
        }

        static final StringBuilder appendTo(final StringBuilder sb, final @Nullable String module,
                final Unqualified identifier) {
            if (module != null) {
                sb.append(module).append(':');
            }
            return sb.append(identifier.getLocalName());
        }
    }

    /**
     * An {@code api-identifier} step in a {@link ApiPath}.
     */
    public static final class ApiIdentifier extends Step {
        @Deprecated(since = "7.0.4", forRemoval = true)
        public ApiIdentifier(final @Nullable String module, final String identifier) {
            super(module, identifier);
        }

        public ApiIdentifier(final @Nullable String module, final Unqualified identifier) {
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

        ListInstance(final @Nullable String module, final Unqualified identifier,
                final ImmutableList<String> keyValues) {
            super(module, identifier);
            this.keyValues = requireNonNull(keyValues);
        }

        public static ListInstance of(final @Nullable String module, final Unqualified identifier, final String value) {
            return new ListInstance(module, identifier, ImmutableList.of(value));
        }

        public static ListInstance of(final @Nullable String module, final Unqualified identifier,
                final String... values) {
            return of(module, identifier, ImmutableList.copyOf(values));
        }

        public static ListInstance of(final @Nullable String module, final Unqualified identifier,
                final List<String> values) {
            return of(module, identifier, ImmutableList.copyOf(values));
        }

        public static ListInstance of(final @Nullable String module, final Unqualified identifier,
                final ImmutableList<String> values) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException(
                    appendTo(new StringBuilder("empty values for "), module, identifier).toString());
            }
            return new ListInstance(module, identifier, values);
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

        @Override
        void appendTo(final StringBuilder sb) {
            super.appendTo(sb);
            sb.append('=');
            final var it = keyValues.iterator();
            while (true) {
                sb.append(PERCENT_ESCAPER.escape(it.next()));
                if (it.hasNext()) {
                    sb.append(',');
                } else {
                    break;
                }
            }
        }
    }

    // Escaper based on RFC8040-requirement to percent-encode reserved characters, as defined in
    // https://tools.ietf.org/html/rfc3986#section-2.2
    public static final Escaper PERCENT_ESCAPER;

    static {
        final var hexFormat = HexFormat.of().withUpperCase();
        final var builder = Escapers.builder();
        for (char ch : new char[] {
            // Reserved characters as per https://tools.ietf.org/html/rfc3986#section-2.2
            ':', '/', '?', '#', '[', ']', '@',
            '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
        }) {
            builder.addEscape(ch, "%" + hexFormat.toHighHexDigit(ch) + hexFormat.toLowHexDigit(ch));
        }
        PERCENT_ESCAPER = builder.build();
    }

    private static final ApiPath EMPTY = new ApiPath(ImmutableList.of());

    public ApiPath {
        requireNonNull(steps);
    }

    /**
     * Return an empty ApiPath.
     *
     * @return An empty ApiPath.
     */
    public static ApiPath empty() {
        return EMPTY;
    }

    public static ApiPath of(final List<Step> steps) {
        return steps.isEmpty() ? EMPTY : new ApiPath(ImmutableList.copyOf(steps));
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

    /**
     * Return the {@link Step}s of this path.
     *
     * @return Path steps
     */
    public ImmutableList<Step> steps() {
        return steps;
    }

    @Override
    public boolean contains(final ApiPath other) {
        if (this == other) {
            return true;
        }
        final var oit = other.steps.iterator();
        for (var step : steps) {
            if (!oit.hasNext() || !step.equals(oit.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the index of a Step in this path matching specified module and identifier. This method is equivalent to
     * {@code indexOf(module, identifier, 0)}.
     *
     * @param module Requested {@link Step#module()}
     * @param identifier Requested {@link Step#identifier()}
     * @return Index of the step in {@link #steps}, or {@code -1} if a matching step is not found
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    public int indexOf(final String module, final String identifier) {
        return indexOf(module, identifier, 0);
    }

    /**
     * Returns the index of a Step in this path matching specified module and identifier, starting search at specified
     * index.
     *
     * @param module Requested {@link Step#module()}
     * @param identifier Requested {@link Step#identifier()}
     * @param fromIndex index from which to search
     * @return Index of the step in {@link #steps}, or {@code -1} if a matching step is not found
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    public int indexOf(final String module, final String identifier, final int fromIndex) {
        final var id = requireNonNull(identifier);
        for (int i = fromIndex, size = steps.size(); i < size; ++i) {
            final var step = steps.get(i);
            if (id.equals(step.identifier.getLocalName()) && Objects.equals(module, step.module)) {
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
        return subList == steps ? this : of(subList);
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this || obj instanceof ApiPath other && steps.equals(other.steps());
    }

    @Override
    public String toString() {
        if (steps.isEmpty()) {
            return "";
        }
        final var sb = new StringBuilder();
        final var it = steps.iterator();
        while (true) {
            it.next().appendTo(sb);
            if (it.hasNext()) {
                sb.append('/');
            } else {
                break;
            }
        }
        return sb.toString();
    }

    @java.io.Serial
    Object writeReplace() throws ObjectStreamException {
        return new APv1(toString());
    }

    @java.io.Serial
    private void writeObject(final ObjectOutputStream stream) throws IOException {
        throw nse();
    }

    @java.io.Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        throw nse();
    }

    @java.io.Serial
    private void readObjectNoData() throws ObjectStreamException {
        throw nse();
    }

    private NotSerializableException nse() {
        return new NotSerializableException(getClass().getName());
    }

    private static ApiPath parseString(final ApiPathParser parser, final String str) throws ParseException {
        return of(parser.parseSteps(str));
    }
}
