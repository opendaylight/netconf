/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5">Content Match Node</a>.
 */
@NonNullByDefault
public record ContentMatchNode(NamespaceSelection selection, Map<QName, Object> qNameValueMap) implements Sibling {
    public ContentMatchNode {
        requireNonNull(selection);
        for (final var value : qNameValueMap.values()) {
            AttributeMatch.checkValue(value);
        }
    }
}
