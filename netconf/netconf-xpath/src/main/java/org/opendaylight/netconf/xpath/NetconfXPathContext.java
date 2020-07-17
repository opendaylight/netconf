/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

/**
 * Holder of XPath context for get or get-config RPC with filter parameter.
 *
 */
public class NetconfXPathContext {

    private static final String XMLNS = "xmlns";
    private static final String BASE_NS_PREFIX = "nxpcrpc";

    private static final List<XPathQName> NAMESPACES = new ArrayList<>();
    private final AtomicInteger atomicInteger = new AtomicInteger(0);

    private String xpath;
    private Optional<YangInstanceIdentifier> path = Optional.empty();

    /**
     * Allows to create empty context.
     *
     * @return empty context
     */
    public static NetconfXPathContext empty() {
        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext("/*");
        netconfXPathContext.setPath(YangInstanceIdentifier.empty());
        return netconfXPathContext;
    }

    /**
     * Create the XPath with mapped prefixed for namespaces based on the
     * {@link YangInstanceIdentifier} and list of additional filters.
     *
     * @param basePath    base path
     * @param filtersList list of additional filters
     * @return new XPath context specific for get and get-config operations via
     *         Netconf
     */
    public static NetconfXPathContext createXPathContext(YangInstanceIdentifier basePath,
            List<Set<QName>> filtersList) {
        final StringBuilder sb = new StringBuilder();
        final List<String> namespaces = new ArrayList<>();

        mapBasePathToXPath(basePath, sb, namespaces);

        final StringBuilder baseXPath = new StringBuilder(sb.toString());
        final List<String> xpathParts = new ArrayList<>();
        mapFiltersToBaseXpath(filtersList, namespaces, baseXPath, xpathParts);

        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext(
                xpathParts.stream().collect(Collectors.joining(" | ")));
        namespaces.forEach(netconfXPathContext::addNamespace);
        netconfXPathContext.setPath(basePath);
        return netconfXPathContext;
    }

    /**
     * Allows to use XPath manually. Do not forget to map namespaces with
     * {@link NetconfXPathContext#addNamespace(String)}.
     *
     * @param xpath XPath as String
     */
    public NetconfXPathContext(String xpath) {
        this.xpath = xpath;
    }

    /**
     * Add new namespace. It's mapping the new namespace with all occurrences in
     * existing XPath.
     *
     * @param namespace new namespace
     */
    public synchronized void addNamespace(String namespace) {
        final String namespacePrefix = new StringBuilder(BASE_NS_PREFIX).append(atomicInteger.getAndIncrement())
                .toString();
        mapNamespace(namespacePrefix, namespace);
        NAMESPACES.add(new XPathQName(XMLNS, namespacePrefix, namespace));
    }

    private void mapNamespace(String namespacePrefix, String namespace) {
        xpath = xpath.replace(namespace, namespacePrefix);
    }

    public List<XPathQName> getNamespaces() {
        return Collections.unmodifiableList(NAMESPACES);
    }

    public String getXpathWithPrefixes() {
        return xpath;
    }

    public void setPath(YangInstanceIdentifier path) {
        if (this.path.isEmpty()) {
            this.path = Optional.of(path);
        }
    }

    public Optional<YangInstanceIdentifier> getPath() {
        return path;
    }

    private static void mapFiltersToBaseXpath(List<Set<QName>> filtersList, final List<String> namespaces,
            final StringBuilder baseXPath, final List<String> xpathParts) {
        final Iterator<Set<QName>> qnames = filtersList.iterator();
        while (qnames.hasNext()) {
            final Set<QName> filters = qnames.next();
            if (qnames.hasNext()) {
                final QName path = filters.iterator().next();
                final String ns = path.getNamespace().toString();
                namespaces.add(ns);
                baseXPath.append("/").append(ns).append(":").append(path.getLocalName());
            } else {
                filters.forEach(filter -> {
                    final StringBuilder sbXPathPart = new StringBuilder(baseXPath);
                    final String ns = filter.getNamespace().toString();
                    namespaces.add(ns);
                    sbXPathPart.append("/").append(ns).append(":").append(filter.getLocalName());
                    xpathParts.add(sbXPathPart.toString());
                });
            }
        }
    }

    private static void mapBasePathToXPath(YangInstanceIdentifier basePath, final StringBuilder sb,
            final List<String> namespaces) {
        basePath.getPathArguments().forEach(p -> {
            if (p instanceof NodeIdentifierWithPredicates) {
                ((NodeIdentifierWithPredicates) p).entrySet().forEach(entry -> {
                    sb.append('[');
                    appendQNameWithNs(sb, namespaces, entry.getKey());
                    sb.append("/text()='");
                    sb.append(entry.getValue());
                    sb.append("']");
                });
                return;
            }
            final QName nodeType = p.getNodeType();
            appendQNameWithNs(sb, namespaces, nodeType);
        });
    }

    private static void appendQNameWithNs(final StringBuilder sb, final List<String> namespaces, final QName nodeType) {
        final String namespace = nodeType.getNamespace().toString();
        namespaces.add(namespace);
        sb.append("/");
        sb.append(namespace);
        sb.append(":");
        sb.append(nodeType.getLocalName());
    }

}