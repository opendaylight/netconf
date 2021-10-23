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
import java.net.URI;
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
     * A selector for a single node as identified by {@link #path()}. Individual child nodes are subject to further
     * filtering based on {@link #subSelectors()}.
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

        /**
         * Return the path to the selected node. Guaranteed to have at least one element.
         *
         * @return path to the selected node
         */
        public ImmutableList<ApiIdentifier> path() {
            return path;
        }

        /**
         * Selectors for single nodes which should be selected from the node found by interpreting {@link #path}. If
         * there are no selectors, i.e. {@code subSelectors().isEmpty())}, all child nodes are meant to be selected.
         *
         * @return Selectors for nested nodes.
         */
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

    private static final URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:fields:1.0");

    private final ImmutableList<NodeSelector> nodeSelectors;

    FieldsParameter(final ImmutableList<NodeSelector> nodeSelectors) {
        this.nodeSelectors = requireNonNull(nodeSelectors);
        checkArgument(!nodeSelectors.isEmpty(), "At least one selector is required");
    }

    /**
     * Parse a {@code fields} parameter.
     *
     * @param str Unescaped URL string
     * @return The contents of parameter
     * @throws ParseException if {@code str} does not represent a valid {@code fields} parameter.
     */
    public static FieldsParameter parse(final String str) throws ParseException {
        return new FieldsParameterParser().parse(str);
    }

    public static String uriName() {
        return "fields";
    }

    public static URI capabilityUri() {
        return CAPABILITY;
    }

    /**
     * Selectors for nodes which should be reported. Guaranteed to have at least one element.
     *
     * @return selectors for nodes to be reported
     */
    public ImmutableList<NodeSelector> nodeSelectors() {
        return nodeSelectors;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("nodeSelectors", nodeSelectors).toString();
    }
}
