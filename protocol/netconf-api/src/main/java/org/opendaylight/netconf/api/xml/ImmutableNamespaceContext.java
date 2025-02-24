/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static java.util.Objects.requireNonNull;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
import static javax.xml.XMLConstants.XML_NS_PREFIX;
import static javax.xml.XMLConstants.XML_NS_URI;

import com.google.common.base.MoreObjects;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An immutable {@link NamespaceContext}.
 */
record ImmutableNamespaceContext(
        ImmutableListMultimap<String, String> uriToPrefix,
        ImmutableBiMap<String, String> prefixToUri) implements NamespaceContext {
    private static final ImmutableBiMap<String, String> FIXED_PREFIX_TO_URI = ImmutableBiMap.of(
        XML_NS_PREFIX, XML_NS_URI,
        XMLNS_ATTRIBUTE, XMLNS_ATTRIBUTE_NS_URI);

    static final ImmutableNamespaceContext EMPTY = of(Map.of());

    static ImmutableNamespaceContext of(final Map<String, String> prefixToUri) {
        final var uriToPrefixBuilder = ImmutableListMultimap.<String, String>builder();
        final var prefixToUriBuilder = HashBiMap.<String, String>create();

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
        for (var entry : prefixToUri.entrySet()) {
            final var prefix = requireNonNull(entry.getKey());
            if (!prefix.isEmpty()) {
                final var namespaceURI = requireNonNull(entry.getValue());
                checkMapping(prefix, namespaceURI);
                uriToPrefixBuilder.put(namespaceURI, prefix);
                prefixToUriBuilder.putIfAbsent(prefix, namespaceURI);
            }
        }

        return new ImmutableNamespaceContext(uriToPrefixBuilder.build(), ImmutableBiMap.copyOf(prefixToUriBuilder));
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
        return uriToPrefix.get(rejectNull(namespaceURI)).iterator();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("prefixToUri", prefixToUri).toString();
    }

    private static void checkMapping(final String prefix, final String namespaceURI) {
        if (namespaceURI.isEmpty()) {
            throw new IllegalArgumentException("Namespace must not be empty (%s)".formatted(prefix));
        }
        final var pref = FIXED_PREFIX_TO_URI.get(prefix);
        if (pref != null) {
            throw new IllegalArgumentException(
                "Cannot rebind prefix %s from %s to %s".formatted(prefix, pref, namespaceURI));
        }
        if (FIXED_PREFIX_TO_URI.containsValue(namespaceURI)) {
            throw new IllegalArgumentException("Cannot bind namespace %s".formatted(namespaceURI));
        }
    }

    private static String getValue(final ImmutableBiMap<String, String> map, final String key,
            final String defaultValue) {
        final var found = map.get(rejectNull(key));
        return found != null ? found : defaultValue;
    }

    // Peculiarity of NamespaceContext: nulls are rejected with IAE instead of NPE as would be usual
    private static @NonNull String rejectNull(final @Nullable String str) {
        if (str == null) {
            throw new IllegalArgumentException();
        }
        return str;
    }
}
