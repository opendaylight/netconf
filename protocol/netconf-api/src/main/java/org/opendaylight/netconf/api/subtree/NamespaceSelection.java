/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
@NonNullByDefault
public sealed interface NamespaceSelection {
    /**
     * An exact match.
     */
    record Exact(String namespace, String name) implements NamespaceSelection {
        public Exact {
            if (namespace.isEmpty()) {
                throw new IllegalArgumentException("empty namespace");
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("empty name");
            }
        }
    }

    /**
     * A wildcard match.
     */
    record Wildcard(String name) implements NamespaceSelection {
        public Wildcard {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("empty name");
            }
        }
    }
}
