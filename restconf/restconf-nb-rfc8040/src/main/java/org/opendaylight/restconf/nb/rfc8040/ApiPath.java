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
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
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
        return str.isEmpty() ?  EMPTY : new ApiPath(ApiPathParser.parseSteps(str));
    }

    @VisibleForTesting
    ImmutableList<Step> steps() {
        return steps;
    }
}
