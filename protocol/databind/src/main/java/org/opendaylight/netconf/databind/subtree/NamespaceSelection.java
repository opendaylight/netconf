/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
@NonNullByDefault
public sealed interface NamespaceSelection {
    /**
     * An exact match.
     */
    record Exact(QName qname) implements NamespaceSelection {
        public Exact {
            requireNonNull(qname);
        }
    }

    /**
     * A wildcard match.
     */
    record Wildcard(Unqualified name, List<QName> qnames) implements NamespaceSelection {
        public Wildcard {
            qnames.stream().forEach(qname -> {
                if (!name.getLocalName().equals(qname.getLocalName())) {
                    throw new IllegalArgumentException(qname + " does not match name " + name.getLocalName());
                }
            });
        }

        // Define equals to compare two wildcards ignoring order of the qnames in the list
        @Override
        public boolean equals(final @Nullable Object obj) {
            return obj == this || obj instanceof Wildcard other && name.equals(other.name)
                && qnames.size() == other.qnames.size() && qnames.containsAll(other.qnames);
        }
    }
}
