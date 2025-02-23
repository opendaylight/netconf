/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Common trait of {@link AttributeMatch} and {@link ContentMatchNode}: both have a String {@link #value()} that needs
 * to be matched.
 */
@NonNullByDefault
sealed interface StringMatch extends NamespaceSelection permits AttributeMatch, ContentMatchNode {
    /**
     * Returns the value to be matched.
     *
     * @return the value to be matched
     */
    String value();
}
