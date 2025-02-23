/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
public sealed interface NamespaceSelection extends Immutable {
    /**
     * Returns the element local name.
     *
     * @return the element local name
     */
    @NonNull String name();

    /**
     * Returns the namespace, if present. If not present, this selection is to be treated as a namespace wildcard,
     *
     * @return the namespace, or {@code null}
     */
    @Nullable String namespace();

    record Exact(@NonNull String name, @NonNull String namespace) implements NamespaceSelection {
        public Exact {
            requireNonNull(name);
            requireNonNull(namespace);
        }
    }

    record Wildcard(@NonNull String name) implements NamespaceSelection {
        public Wildcard {
            requireNonNull(name);
        }

        @Override
        public @Nullable String namespace() {
            return null;
        }
    }
}
