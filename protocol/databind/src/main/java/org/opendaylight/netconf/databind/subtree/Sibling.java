/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A single sibling in a {@link SiblingSet}.
 */
@NonNullByDefault
public sealed interface Sibling permits ContainmentNode, ContentMatchNode, SelectionNode {
    /**
     * Return the {@link NamespaceSelection} of this sibling.
     *
     * @return the namespace selection
     */
    NamespaceSelection selection();
}
