/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam.NodeSelector;
import org.opendaylight.yangtools.yang.common.YangNames;

/**
 * Stateful parser for {@link FieldsParam}. This is not as hard as IETF's ABNF would lead you to believe. The original
 *  definition is:
 * <pre>
 *    fields-expr = path "(" fields-expr ")" / path ";" fields-expr / path
 *    path = api-identifier [ "/" path ]
 * </pre>
 * To make some sense of this, let's express the same constructs in a more powerful ANTLR4 grammar.
 *
 * <p>
 * {@code path} is a rather simple
 * <pre>
 *    path = api-identifier ("/" api-identifier)*
 * </pre>
 * which is to say a {@code path} is "a sequence of one or more api-identifiers, separated by slashes". This boils in
 * turn down to a list {@link ApiIdentifier}s, which is guaranteed to have at least one item.
 *
 * <p>
 * {@code fields-expr} can be rewritten as three distinct possibilities:
 * <pre>
 *    fields-expr : path "(" fields-expr ")"
 *                | path ";" fields-expr
 *                | path
 * </pre>
 * which makes it clear it is a recursive structure, where the parentheses part is sub-filters and ';' serves as
 * concatenation. So let's rewrite that by folding the common part and use optional elements and introducing proper
 * names for constructs
 * <pre>
 *   fields         : node-selectors EOF
 *   node-selectors : node-selector (";" node-selector)*
 *   node-selector  : path sub-selectors?
 *   sub-selectors  : "(" node-selectors ")"
 *   path           : api-identifier ("/" api-identifier)*
 * </pre>
 *
 * <p>
 * That ANTLR4 grammar dictates the layout of {@link FieldsParam}. It also shows the parsing is recursive on
 * {@code node-selectors}, which is what {@link #parse(String)} and
 * {@link NodeSelectorParser#parseSubSelectors(String, int)} deal with.
 */
final class FieldsParameterParser {
    // Lazily instantiated queue for reuse of parser when we encounter sub-selectors. We could just rely on JIT/GC
    // dealing with allocation rate, but we should be ready to see malicious inputs. One example of that is
    // multiple nested sub-selectors like "a(b(c(d)));e(f(g(h)));i(j(k(l)))" With this cache we end allocating only four
    // parsers instead of ten.
    private Deque<NodeSelectorParser> parsers;

    @NonNull FieldsParam parse(final String str) throws ParseException {
        final var nodeSelectors = ImmutableList.<NodeSelector>builder();

        int idx = 0;
        final var parser = new NodeSelectorParser();
        while (true) {
            final int next = parser.fillFrom(str, idx);
            nodeSelectors.add(parser.collectAndReset());

            if (next == str.length()) {
                // We have reached the end, we are done
                return new FieldsParam(nodeSelectors.build());
            }

            final char ch = str.charAt(next);
            if (ch != ';') {
                throw new ParseException("Expecting ';', not '" + ch + "'", next);
            }
            idx = next + 1;
        }
    }

    private @NonNull NodeSelectorParser getParser() {
        final var local = parsers;
        if (local != null) {
            final var existing = local.poll();
            if (existing != null) {
                return existing;
            }
        }
        return new NodeSelectorParser();
    }

    private void putParser(final NodeSelectorParser parser) {
        var local = parsers;
        if (local == null) {
            // Let's be conservative with memory allocation
            parsers = local = new ArrayDeque<>(2);
        }
        local.push(parser);
    }

    private static void expectIdentifierStart(final String str, final int offset) throws ParseException {
        final char ch = charAt(str, offset);
        if (!YangNames.IDENTIFIER_START.matches(ch)) {
            throw new ParseException("Expecting [a-ZA-Z_], not '" + ch + "'", offset);
        }
    }

    private static char charAt(final String str, final int offset) throws ParseException {
        if (str.length() == offset) {
            throw new ParseException("Unexpected end of input", offset);
        }
        return str.charAt(offset);
    }

    // A note here: we could store 'str' either in this object, or FieldsParameterParser, but that makes it a bit
    // removed via indirection. We are opting for explicit argument passing to ensure JIT sees it as a local variable
    // along with offset.
    private final class NodeSelectorParser {
        private final List<ApiIdentifier> path = new ArrayList<>(4);

        // Not that common: lazily instantiated
        private List<NodeSelector> selectors;

        int fillFrom(final String str, final int offset) throws ParseException {
            return parsePathStepFirst(str, offset);
        }

        @NonNull NodeSelector collectAndReset() {
            final ImmutableList<ApiIdentifier> collectedPath = ImmutableList.copyOf(path);
            path.clear();

            final ImmutableList<NodeSelector> collectedSelectors;
            if (selectors != null && !selectors.isEmpty()) {
                collectedSelectors = ImmutableList.copyOf(selectors);
                selectors.clear();
            } else {
                collectedSelectors = ImmutableList.of();
            }

            return new NodeSelector(collectedPath, collectedSelectors);
        }

        // We are at the start of a step in path. We are dealing with the first part of
        //   identifier (":" identifier)?
        // but are mindful of the big picture
        private int parsePathStepFirst(final String str, final int offset) throws ParseException {
            expectIdentifierStart(str, offset);

            int idx = offset + 1;
            while (true) {
                if (idx == str.length()) {
                    path.add(new ApiIdentifier(null, str.substring(offset)));
                    return idx;
                }

                final char ch = str.charAt(idx);
                if (!YangNames.NOT_IDENTIFIER_PART.matches(ch)) {
                    idx++;
                    continue;
                }

                final String first = str.substring(offset, idx);
                if (ch == ':') {
                    // We have complete first identifier, now switch to parsing the second identifier
                    return parsePathStepSecond(first, str, idx + 1);
                }
                path.add(new ApiIdentifier(null, first));

                switch (ch) {
                    case ';':
                    case ')':
                        // End of this selector, return
                        return idx;
                    case '/':
                        // Process next step
                        return parsePathStepFirst(str, idx + 1);
                    case '(':
                        // Process at least one sub-selector
                        return parseSubSelectors(str, idx + 1);
                    default:
                        throw new ParseException("Expecting [a-zA-Z_.-/(:;], not '" + ch + "'", idx);
                }
            }
        }

        // We are at the second identifier of a step in path, we already have the first identifier from
        //   identifier (":" identifier)?
        // but are mindful of the big picture
        private int parsePathStepSecond(final String module, final String str, final int offset) throws ParseException {
            expectIdentifierStart(str, offset);

            int idx = offset + 1;
            while (true) {
                if (idx == str.length()) {
                    path.add(new ApiIdentifier(module, str.substring(offset)));
                    return idx;
                }

                final char ch = str.charAt(idx);
                if (!YangNames.NOT_IDENTIFIER_PART.matches(ch)) {
                    idx++;
                    continue;
                }
                path.add(new ApiIdentifier(module, str.substring(offset, idx)));

                switch (ch) {
                    case ';':
                    case ')':
                        // End of this selector, return
                        return idx;
                    case '/':
                        // Process next step
                        return parsePathStepFirst(str, idx + 1);
                    case '(':
                        // Process at least one sub-selector
                        return parseSubSelectors(str, idx + 1);
                    default:
                        throw new ParseException("Expecting [a-zA-Z_.-/(:;], not '" + ch + "'", idx);
                }
            }
        }

        // We are dealing with sub-selectors here
        private int parseSubSelectors(final String str, final int offset) throws ParseException {
            var local = selectors;
            if (local == null) {
                selectors = local = new ArrayList<>(4);
            }

            int idx = offset;
            final var parser = getParser();
            while (true) {
                final int next = parser.fillFrom(str, idx);
                local.add(parser.collectAndReset());

                final char ch = charAt(str, next);
                switch (ch) {
                    case ';':
                        // Another sub-selector
                        idx = next + 1;
                        continue;
                    case ')':
                        // End of these sub-selectors, return the parser for reuse
                        putParser(parser);
                        return next + 1;
                    default:
                        throw new ParseException("Expecting [;)], not '" + ch + "'", next);
                }
            }
        }
    }
}
