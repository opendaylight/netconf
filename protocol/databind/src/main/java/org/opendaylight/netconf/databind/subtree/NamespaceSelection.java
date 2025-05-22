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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
@NonNullByDefault
public sealed interface NamespaceSelection {
    /**
     * Return {@code true} if this selection can match the supplied QName.
     */
    boolean matches(QName qname);

    /**
     * An exact match.
     */
    record Exact(QName qname) implements NamespaceSelection {
        public Exact {
            requireNonNull(qname);
        }


        @Override public boolean matches(QName other) {
            return qname.equals(other);
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

        @Override public boolean matches(QName other) {
            // Wildcard matches any QName whose local-name equals `name`
            return name.getLocalName().equals(other.getLocalName());
        }
    }
}
