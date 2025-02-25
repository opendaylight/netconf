/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import com.google.common.annotations.Beta;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.NamespaceURN;

/**
 * Set of utilities to go with XML processing.
 */
@NonNullByDefault
public final class XMLSupport {
    private static final XMLOutputFactory XML_FACTORY;
    private static final NamespaceSetter XML_NAMESPACE_SETTER;

    static {
        final var factory = XMLOutputFactory.newFactory();
        // FIXME: not repairing namespaces is probably common, this should be availabe as common XML constant.
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);

        try {
            XML_NAMESPACE_SETTER = NamespaceSetter.forFactory(factory, "op", NamespaceURN.BASE);
        } catch (XMLStreamException e) {
            throw new ExceptionInInitializerError(e);
        }
        XML_FACTORY = factory;
    }

    private XMLSupport() {
        // hidden on purpose
    }

    public static XMLStreamWriter newStreamWriter(final Result result) throws XMLStreamException {
        return XML_FACTORY.createXMLStreamWriter(result);
    }

    public static XMLStreamWriter newNetconfStreamWriter(final Result result) throws XMLStreamException {
        final var writer = newStreamWriter(result);
        XML_NAMESPACE_SETTER.initializeNamespace(writer);
        return writer;
    }

    @Beta
    public static NamespaceContext namespaceContextOf(final Map<String, String> prefixToUri) {
        return prefixToUri.isEmpty() ? ImmutableNamespaceContext.EMPTY : ImmutableNamespaceContext.of(prefixToUri);
    }
}
