/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * An <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2">Attribute Match Expression</a>.
 *
 * @param selection exact selection
 * @param value the value
 */
@NonNullByDefault
public record AttributeMatch(NamespaceSelection.Exact selection, Object value) {
    public AttributeMatch {
        requireNonNull(selection);
        value = checkValue(value);
    }

    @SuppressWarnings("null")
    static Object checkValue(final Object value) {
        return switch (value) {
            case byte[] v -> v;
            case Boolean v -> v;
            case Decimal64 v -> v;
            case Empty v -> v;
            case QName v -> v;
            case Set<?> v -> v;
            case YangInstanceIdentifier v -> v;
            case Byte v -> v;
            case Short v -> v;
            case Integer v -> v;
            case Long v -> v;
            case Uint8 v -> v;
            case Uint16 v -> v;
            case Uint32 v -> v;
            case Uint64 v -> v;
            case String v -> v;
            default -> throw new IllegalArgumentException("Unsupported value " + value);
        };
    }
}
