/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

final class DefaultNamespaceSetter extends NamespaceSetter {
    private final NamespaceContext namespaceContext;

    DefaultNamespaceSetter(final NamespaceContext namespaceContext) {
        this.namespaceContext = requireNonNull(namespaceContext);
    }

    @Override
    void initializeNamespace(final XMLStreamWriter writer) throws XMLStreamException {
        writer.setNamespaceContext(namespaceContext);
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("ns", namespaceContext);
    }
}