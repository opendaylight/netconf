/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1">Namespace Selection</a>.
 */
@NonNullByDefault
public sealed interface NamespaceSelection extends Immutable permits AbstractNamespaceSelection, StringMatch {
    /**
     * Returns the element local name.
     *
     * @return the element local name
     */
    String name();

    /**
     * Returns the namespace, if present. If not present, this selection is to be treated as a namespace wildcard,
     *
     * @return the namespace, or {@code null}
     */
    @Nullable XMLNamespace namespace();
}
