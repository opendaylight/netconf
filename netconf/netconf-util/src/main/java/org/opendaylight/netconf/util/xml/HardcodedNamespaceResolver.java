/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.xml;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;

// http://www.ibm.com/developerworks/library/x-nmspccontext/
public class HardcodedNamespaceResolver implements NamespaceContext {
    private final Map<String/* prefix */, String/* namespace */> prefixesToNamespaces;

    public HardcodedNamespaceResolver(final String prefix, final String namespace) {
        this(ImmutableMap.of(prefix, namespace));
    }

    public HardcodedNamespaceResolver(final Map<String, String> prefixesToNamespaces) {
        this.prefixesToNamespaces = Collections.unmodifiableMap(prefixesToNamespaces);
    }

    /**
     * Returns the URI for all prefixes needed. Wherever possible it
     * uses {@code XMLConstants}.
     *
     * @param prefix    prefix
     * @return uri      uniform resource identifier
     */
    @Override
    public String getNamespaceURI(final String prefix) {
        if (prefixesToNamespaces.containsKey(prefix)) {
            return prefixesToNamespaces.get(prefix);
        }

        throw new IllegalStateException("Prefix mapping not found for " + prefix);
    }

    @Override
    public String getPrefix(final String namespaceURI) {
        // Not needed in this context.
        return null;
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
        // Not needed in this context.
        return null;
    }
}
