/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter.NodeSelector;
import org.opendaylight.yangtools.yang.common.YangNames;

final class NodeSelectorParser {
    private final Builder<ApiIdentifier> path = ImmutableList.builder();
    private final String str;

    // Not that common: lazily instantiated
    private Builder<NodeSelector> subSelectors;

    NodeSelectorParser(final String str) {
        this.str = requireNonNull(str);
    }

    @NonNull NodeSelector toSelector() {
        return new NodeSelector(path.build(), subSelectors == null ? ImmutableList.of() : subSelectors.build());
    }

    int processCharacters(final int offset) throws ParseException {
        return parsePathStepFirst(offset);
    }

    // We are at the start of a step in path. We are dealing with the first part of
    //   identifier (":" identifier)?
    // but are mindful of the big picture
    private int parsePathStepFirst(final int offset) throws ParseException {
        expectIdentifierStart(offset);

        int idx = offset + 1;
        while (true) {
            if (idx == str.length()) {
                path.add(new ApiIdentifier(str.substring(offset)));
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
            path.add(new ApiIdentifier(first));

            switch (ch) {
                case ';':
                case ')':
                    // End of this selector, return
                    return idx;
                case '/':
                    // Process next step
                    return parsePathStepFirst(idx + 1);
                case '(':
                    return parseSubSelectors(idx + 1);
                default:
                    throw new ParseException("Expecting [a-zA-Z_.-/(:;], not '" + ch + "'", idx);
            }
        }
    }

    // We are at the second identifier of a step in path, we already have the first identifier from
    //   identifier (":" identifier)?
    // but are mindful of the big picture
    private int parsePathStepSecond(final String module, final String str, final int offset) throws ParseException {
        expectIdentifierStart(offset);

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
                    return parsePathStepFirst(idx + 1);
                case '(':
                    return parseSubSelectors(idx + 1);
                default:
                    throw new ParseException("Expecting [a-zA-Z_.-/(:;], not '" + ch + "'", idx);
            }
        }
    }

    // We are dealing with sub-selectors here
    private int parseSubSelectors(final int offset) throws ParseException {
        var local = subSelectors;
        if (local == null) {
            subSelectors = local = ImmutableList.builder();
        }

        int idx = offset;
        while (true) {
            final var sub = new NodeSelectorParser(str);
            final int next = sub.processCharacters(idx);
            local.add(sub.toSelector());

            final char ch = charAt(next);
            switch (ch) {
                case ')':
                    // End of these sub-selectors;
                    return next + 1;
                case ';':
                    idx = next + 1;
                    continue;
                default:
                    throw new ParseException("Expecting [;)], not '" + ch + "'", next);
            }
        }
    }

    private void expectIdentifierStart(final int offset) throws ParseException {
        final char ch = charAt(offset);
        if (!YangNames.IDENTIFIER_START.matches(ch)) {
            throw new ParseException("Expecting [a-ZA-Z_], not '" + ch + "'", offset);
        }
    }

    private char charAt(final int offset) throws ParseException {
        if (str.length() == offset) {
            throw new ParseException("Unexpected end of input", offset);
        }
        return str.charAt(offset);
    }
}
