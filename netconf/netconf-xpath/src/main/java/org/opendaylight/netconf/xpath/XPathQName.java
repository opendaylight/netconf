/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

public class XPathQName {

    private final String namespace;
    private final String namespacePrefix;
    private final String name;

    public XPathQName(String namespace, String namespacePrefix, String name) {
        this.namespace = namespace;
        this.namespacePrefix = namespacePrefix;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }
}
