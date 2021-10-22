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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter.NodeSelector;
import org.opendaylight.yangtools.yang.common.YangNames;

@NonNullByDefault
final class NodeSelectorParser {
    private final Builder<ApiIdentifier> path = ImmutableList.builder();
    private final String str;

    // Not that common: lazily instantiated
    private @Nullable Builder<NodeSelector> subSelectors;

    NodeSelectorParser(final String str) {
        this.str = requireNonNull(str);
    }

    NodeSelector toSelector() {
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
                    // End of this selector, return
                    return idx;
                case '/':
                    // Process next step
                    return parsePathStepFirst(idx + 1);
                case '(':
                    // FIXME: parse sub-selectors
                    throw new IllegalStateException();
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
                    // End of this selector, return
                    return idx;
                case '/':
                    // Process next step
                    return parsePathStepFirst(idx + 1);
                case '(':
                    // FIXME: parse sub-selectors
                    throw new IllegalStateException();
                default:
                    throw new ParseException("Expecting [a-zA-Z_.-/(:;], not '" + ch + "'", idx);
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
