/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import java.util.Map;
import javax.xml.namespace.NamespaceContext;

/**
 * Set of utilities to go with XML processing.
 */
public final class XMLSupport {
    private XMLSupport() {
        // Hidden for now
    }

    public static NamespaceContext fixedNamespaceContext() {
        return ImmutableNamespaceContext.EMPTY;
    }

    public static NamespaceContext fixedNamespaceContextOf(final Map<String, String> prefixToUri) {
        return prefixToUri.isEmpty() ? fixedNamespaceContext() : ImmutableNamespaceContext.of(prefixToUri);
    }
}
