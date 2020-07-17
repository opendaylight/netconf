/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;

/**
 * The QName consists of local name of element and XML namespace, but for XPath,
 * it was added namespace prefix to it.
 */
public class XPathQName {

    private final String namespace;
    private final String namespacePrefix;
    private final String name;

    public XPathQName(final @NonNull String namespace, final @NonNull String namespacePrefix,
            final @NonNull String name) {
        this.namespace = requireNonNull(namespace);
        this.namespacePrefix = requireNonNull(namespacePrefix);
        this.name = requireNonNull(name);
    }

    /**
     * Allows to get a local name of a element.
     *
     * @return local name
     */
    public @NonNull String getName() {
        return name;
    }

    /**
     * Allows to get a namespace of a element.
     *
     * @return namespace
     */
    public @NonNull String getNamespace() {
        return namespace;
    }

    /**
     * Allows to get a prefix for namespace of a element.
     *
     * @return namespace prefix
     */
    public @NonNull String getNamespacePrefix() {
        return namespacePrefix;
    }
}
