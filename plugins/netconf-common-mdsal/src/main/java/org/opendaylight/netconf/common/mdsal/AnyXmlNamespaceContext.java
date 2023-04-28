/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
import static javax.xml.XMLConstants.XML_NS_PREFIX;
import static javax.xml.XMLConstants.XML_NS_URI;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.namespace.NamespaceContext;
import org.opendaylight.yangtools.concepts.Immutable;

final class AnyXmlNamespaceContext implements Immutable, NamespaceContext {
    private static final ImmutableBiMap<String, String> FIXED_PREFIX_TO_URI = ImmutableBiMap.of(
        XML_NS_PREFIX, XML_NS_URI,
        XMLNS_ATTRIBUTE, XMLNS_ATTRIBUTE_NS_URI);

    private final ImmutableListMultimap<String, String> uriToPrefix;
    private final ImmutableBiMap<String, String> prefixToUri;

    AnyXmlNamespaceContext(final Map<String, String> prefixToUri) {
        final ImmutableListMultimap.Builder<String, String> uriToPrefixBuilder = ImmutableListMultimap.builder();
        final BiMap<String, String> prefixToUriBuilder = HashBiMap.create();

        // Populate well-known prefixes
        prefixToUriBuilder.putAll(FIXED_PREFIX_TO_URI);
        uriToPrefixBuilder.putAll(FIXED_PREFIX_TO_URI.inverse().entrySet());

        // Deal with default namespace first ...
        final String defaultURI = prefixToUri.get("");
        if (defaultURI != null) {
            checkMapping("", defaultURI);
            uriToPrefixBuilder.put(defaultURI, "");
            prefixToUriBuilder.putIfAbsent("", defaultURI);
        }

        // ... and then process all the rest
        for (Entry<String, String> entry : prefixToUri.entrySet()) {
            final String prefix = requireNonNull(entry.getKey());
            if (!prefix.isEmpty()) {
                final String namespaceURI = requireNonNull(entry.getValue());
                checkMapping(prefix, namespaceURI);
                uriToPrefixBuilder.put(namespaceURI, prefix);
                prefixToUriBuilder.putIfAbsent(prefix, namespaceURI);
            }
        }

        uriToPrefix = uriToPrefixBuilder.build();
        this.prefixToUri = ImmutableBiMap.copyOf(prefixToUriBuilder);
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        return getValue(prefixToUri, prefix, "");
    }

    @Override
    public String getPrefix(final String namespaceURI) {
        return getValue(prefixToUri.inverse(), namespaceURI, null);
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
        checkArgument(namespaceURI != null);
        return uriToPrefix.get(namespaceURI).iterator();
    }

    Collection<Entry<String, String>> uriPrefixEntries() {
        return uriToPrefix.entries();
    }

    private static void checkMapping(final String prefix, final String namespaceURI) {
        checkArgument(!namespaceURI.isEmpty(), "Namespace must not be empty (%s)", prefix);
        checkArgument(!FIXED_PREFIX_TO_URI.containsKey(prefix), "Cannot bind prefix %s", prefix);
        checkArgument(!FIXED_PREFIX_TO_URI.containsValue(namespaceURI), "Cannot bind namespace %s", namespaceURI);
    }

    private static String getValue(final ImmutableBiMap<String, String> map, final String key,
            final String defaultValue) {
        checkArgument(key != null);
        final String found;
        return (found = map.get(key)) == null ? defaultValue : found;
    }
}
