/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Holder of XPath context for get or get-config RPC with filter parameter. It
 * creates the simple XPath from {@link YangInstanceIdentifier} with the set of
 * additional filters if needed.
 */
public final class NetconfXPathContext {

    // The base prefix that replaces namespaces in xpath even using of counter for each new namespace.
    // Specific prefix with counter is mapped in list with use of XPathQName.
    // Just for xpath purposes.
    private static final String BASE_NS_PREFIX = "nxpcrpc";

    private final List<XPathQName> namespaces = new ArrayList<>();

    private int counter = 0;
    private Optional<YangInstanceIdentifier> path = Optional.empty();
    private String xpath;

    private NetconfXPathContext(final @NonNull String xpath) {
        this.xpath = requireNonNull(xpath);
    }

    /**
     * Create empty context.
     *
     * @return empty context
     */
    public static @NonNull NetconfXPathContext empty() {
        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext("/*");
        netconfXPathContext.setPath(YangInstanceIdentifier.empty());
        return netconfXPathContext;
    }

    /**
     * Create the XPath with mapped prefixes for namespaces based on the
     * {@link YangInstanceIdentifier} and list of additional filters.
     *
     * @param basePath    base path
     * @param filtersList list of additional filters
     * @return new XPath context specific for get and get-config operations via Netconf
     */
    public static @NonNull NetconfXPathContext createXPathContext(
            final @NonNull YangInstanceIdentifier basePath,
            final @NonNull List<Set<QName>> filtersList) {
        final List<String> namespaces = new ArrayList<>();
        final String xpathString = DOMToXpathTransformUtil.domPathToXPath(basePath, filtersList, namespaces);
        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext(xpathString);
        namespaces.forEach(netconfXPathContext::addNamespace);
        netconfXPathContext.setPath(basePath);
        return netconfXPathContext;
    }

    /**
     * Allows to get mapped XML elements.
     *
     * @return list of mapped XML elements as {@link XPathQName}
     */
    public @NonNull List<XPathQName> getNamespaces() {
        return Collections.unmodifiableList(namespaces);
    }

    /**
     * Allows to get parsed XPath with prefixes.
     *
     * @return XPath as {@link String}
     */
    public @NonNull String getXpathWithPrefixes() {
        return xpath;
    }


    /**
     * Allows to get {@link YangInstanceIdentifier} path.
     *
     * @return {@link YangInstanceIdentifier} path
     */
    public @NonNull Optional<YangInstanceIdentifier> getPath() {
        return path;
    }

    /**
     * Allows to set {@link YangInstanceIdentifier} path if doesn't exist.
     *
     * @param path {@link YangInstanceIdentifier} path
     */
    private void setPath(final @NonNull YangInstanceIdentifier path) {
        if (this.path.isEmpty()) {
            this.path = Optional.of(path);
        }
    }

    /**
     * Add new namespace. It's mapping the new namespace with all occurrences in
     * existing XPath.
     *
     * @param namespace new namespace
     */
    private void addNamespace(final @NonNull String namespace) {
        if (namespace.isBlank()) {
            return;
        }
        final String namespacePrefix = new StringBuilder(BASE_NS_PREFIX).append(counter++).toString();
        mapNamespace(namespacePrefix, namespace);
        namespaces.add(new XPathQName(XMLConstants.XMLNS_ATTRIBUTE, namespacePrefix, namespace));
    }

    private void mapNamespace(final @NonNull String namespacePrefix, final @NonNull String namespace) {
        xpath = xpath.replace(namespace, namespacePrefix);
    }
}