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
 * An <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2">Attribute Match Expression</a>.
 *
 * @param selection exact selection
 * @param value the value
 */
@NonNullByDefault
public record AttributeMatch(NamespaceSelection.Exact selection, String value) {
    public AttributeMatch {
        requireNonNull(selection);
        requireNonNull(value);
    }
}
