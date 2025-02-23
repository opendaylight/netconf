/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5">Content Match Node</a>.
 */
@NonNullByDefault
public record ContentMatchNode(NamespaceSelection selection, String value) implements Sibling {
    public ContentMatchNode {
        requireNonNull(selection);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty value");
        }
        checkNotWhitespace("leading", value.charAt(0));
        checkNotWhitespace("trailing", value.charAt(value.length() - 1));
    }

    @Override
    public String value() {
        return value;
    }

    // RFC6241 says 'whitespace', we are assuming it means "XML whitespace", which can be found at
    // https://www.w3.org/TR/xml/#sec-common-syn
    private static void checkNotWhitespace(final String kind, final char ch) {
        if (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t') {
            throw new IllegalArgumentException("values has " + kind + " whitespace");
        }
    }
}
