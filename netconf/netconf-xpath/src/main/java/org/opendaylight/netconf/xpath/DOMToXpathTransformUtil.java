/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

/**
 * Allows to transform {@link YangInstanceIdentifier} to {@link String} XPath.
 *
 */
public final class DOMToXpathTransformUtil {

    private DOMToXpathTransformUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Creates XPath as {@link String} from {@link YangInstanceIdentifier}. Can
     * contain multiple parts based on the number of fields. These parts are
     * separated by '|'.
     *
     * @param basePath    path from the XPath will be created
     * @param filtersList additional fields for path
     * @param namespaces  holder of namespaces used in the created XPath based on
     *                    the base path
     * @return XPath as {@link String}
     */
    public static String domPathToXPath(YangInstanceIdentifier basePath, List<Set<QName>> filtersList,
            final List<String> namespaces) {
        final StringBuilder baseXPath = new StringBuilder(mapBasePartXPath(basePath, namespaces));

        final List<String> xpathParts = new ArrayList<>();
        mapFiltersToBaseXpath(filtersList, namespaces, baseXPath, xpathParts);

        final String xpathString = xpathParts.stream().collect(Collectors.joining(" | "));
        return xpathString;
    }

    private static String mapBasePartXPath(YangInstanceIdentifier basePath, final List<String> namespaces) {
        final StringBuilder sb = new StringBuilder();
        mapBasePathToXPath(basePath, sb, namespaces);
        final String baseXpath = sb.toString();
        return baseXpath;
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
        if (xpathParts.isEmpty()) {
            xpathParts.add(baseXPath.toString());
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
