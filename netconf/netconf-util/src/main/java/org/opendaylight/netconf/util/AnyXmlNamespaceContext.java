/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterators;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.namespace.NamespaceContext;
import org.opendaylight.yangtools.concepts.Immutable;

@Beta
public final class AnyXmlNamespaceContext implements Immutable, NamespaceContext {
    private final ImmutableBiMap<String, String> mappings;

    public AnyXmlNamespaceContext(final Map<String, String> mappings) {
        // FIXME: check consistency
        // TODO: support multiple prefixes per namespaceURI?
        this.mappings = ImmutableBiMap.copyOf(mappings);
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        return mappings.get(prefix);
    }

    @Override
    public String getPrefix(final String namespaceURI) {
        return mappings.inverse().get(namespaceURI);
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
        final String prefix = getPrefix(namespaceURI);
        return prefix == null ? Collections.emptyIterator() : Iterators.singletonIterator(prefix);
    }

    Collection<Entry<String, String>> prefixMapping() {
        return mappings.entrySet();
    }
}
