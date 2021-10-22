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
    // So this is where IETF's ABNFs just shine when you try to implement them. The original definition is:
    //
    //    fields-expr = path "(" fields-expr ")" / path ";" fields-expr / path
    //    path = api-identifier [ "/" path ]
    //
    // To make some sense of this, let's express the same constructs in a more powerful ANTLR4 grammar.
    //
    // 'path' is rather simple:
    //
    //    path = api-identifier ("/" api-identifier)*
    //
    // i.e. a 'path' is meant to say a "a sequence of one or more api-identifiers, separated by slashes". This boils
    // down to a List<ApiIdentifier>, which is guaranteed to have at least one item.
    //
    // 'fields-expr' can be rewritten as three distinct possibilities:
    //
    //    fields-expr : path "(" fields-expr ")"
    //                | path ";" fields-expr
    //                | path
    //
    // which makes it clear it is a recursive structure, where the parentheses part is sub-filters and ';' serves as
    // concatenation. So let's rewrite that by folding the common part and use optional elements and introducing proper
    // names for constructs:
    //
    //   fields         : node-selectors EOF
    //   node-selectors : node-selector (";" node-selector)*
    //   node-selector  : path sub-selectors?
    //   sub-selectors  : "(" node-selectors ")"
    //   path           : api-identifier ("/" api-identifier)*
    //
    // Let's use this to guide our class layout here as well as our parser. Both end up being extremely simple, which is
    // a Good Thing(tm).

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

    private FieldsParameter(final ImmutableList<NodeSelector> nodeSelectors) {
        this.nodeSelectors = requireNonNull(nodeSelectors);
        checkArgument(!nodeSelectors.isEmpty(), "At least one selector is required");
    }

    public static FieldsParameter parse(final String str) throws ParseException {
        //   fields         : node-selectors EOF
        final var nodeSelectors = ImmutableList.<NodeSelector>builder();

        int idx = 0;
        while (true) {
            final var parser = new NodeSelectorParser(str);
            final int next = parser.processCharacters(idx);
            nodeSelectors.add(parser.toSelector());

            if (next == str.length()) {
                // We have reached the end, we are done
                return new FieldsParameter(nodeSelectors.build());
            }

            final char ch = str.charAt(next);
            if (ch != ';') {
                throw new ParseException("Expecting ';', not '" + ch + "'", next);
            }
            idx = next + 1;
        }
    }

    public ImmutableList<NodeSelector> nodeSelectors() {
        return nodeSelectors;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("nodeSelectors", nodeSelectors).toString();
    }
}
