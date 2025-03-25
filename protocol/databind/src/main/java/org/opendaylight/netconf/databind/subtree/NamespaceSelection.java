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
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
@NonNullByDefault
public sealed interface NamespaceSelection {
    /**
     * An exact match.
     */
    record Exact(NodeIdentifier identifier) implements NamespaceSelection {
        public Exact {
            requireNonNull(identifier);
        }
    }

    /**
     * A wildcard match.
     */
    record Wildcard(Unqualified name, List<NodeIdentifier> identifiers) implements NamespaceSelection {
        public Wildcard {
            identifiers.stream().forEach(identifier -> {
                if (!name.getLocalName().equals(identifier.getNodeType().getLocalName())) {
                    throw new IllegalArgumentException(identifier + " does not match name " + name.getLocalName());
                }
            });
        }
    }
}
