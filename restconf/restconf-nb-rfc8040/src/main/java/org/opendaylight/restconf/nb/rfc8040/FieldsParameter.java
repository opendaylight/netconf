/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * This class represents a "fields" parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.3">RFC8040 section 4.8.3</a>.
 */
@Beta
@NonNullByDefault
public final class FieldsParameter implements Immutable {
    /**
     * A selector for a single child node.
     */
    public static final class NodeSelector implements Immutable {
        private final ImmutableList<ApiIdentifier> path;
        private final ImmutableList<NodeSelector> subSelectors;

        NodeSelector(final ImmutableList<ApiIdentifier> path, final ImmutableList<NodeSelector> subSelectors) {
            this.path = requireNonNull(path);
            this.subSelectors = requireNonNull(subSelectors);
            checkArgument(!path.isEmpty(), "At least path segment is required");
        }

        NodeSelector(final ImmutableList<ApiIdentifier> path) {
            this(path, ImmutableList.of());
        }

        public ImmutableList<ApiIdentifier> path() {
            return path;
        }

        public ImmutableList<NodeSelector> subSelectors() {
            return subSelectors;
        }

        @Override
        public String toString() {
            final var helper = MoreObjects.toStringHelper(this).add("path", path);
            if (!subSelectors.isEmpty()) {
                helper.add("subSelectors", subSelectors);
            }
            return helper.toString();
        }
    }

    private final ImmutableList<NodeSelector> nodeSelectors;

    FieldsParameter(final ImmutableList<NodeSelector> nodeSelectors) {
        this.nodeSelectors = requireNonNull(nodeSelectors);
        checkArgument(!nodeSelectors.isEmpty(), "At least one selector is required");
    }

    public static FieldsParameter parse(final String str) throws ParseException {
        return new FieldsParameterParser().parse(str);
    }

    public ImmutableList<NodeSelector> nodeSelectors() {
        return nodeSelectors;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("nodeSelectors", nodeSelectors).toString();
    }
}
