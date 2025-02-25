/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shim interface to handle differences around namespace handling between various XMLStreamWriter implementations.
 * Specifically:
 * <ul>
 *   <li>OpenJDK DOM writer (com.sun.xml.internal.stream.writers.XMLDOMWriterImpl) throws
 *       UnsupportedOperationException from its setNamespaceContext() method</li>
 *   <li>Woodstox DOM writer (com.ctc.wstx.dom.WstxDOMWrappingWriter) works with namespace context, but treats
 *       setPrefix() calls as hints -- which are not discoverable.</li>
 * </ul>
 *
 * <p>Due to this we perform a quick test for behavior and decide the appropriate strategy.
 */
@NonNullByDefault
abstract sealed class NamespaceSetter permits DefaultNamespaceSetter, FallbackNamespaceSetter {
    private static final Logger LOG = LoggerFactory.getLogger(NamespaceSetter.class);

    abstract void initializeNamespace(XMLStreamWriter writer) throws XMLStreamException;

    static NamespaceSetter forFactory(final XMLOutputFactory xmlFactory, final String prefix, final String uri)
            throws XMLStreamException {
        final var namespaceContext = XMLSupport.namespaceContextOf(Map.of(prefix, uri));

        final var writer = xmlFactory.createXMLStreamWriter(new DOMResult(XmlUtil.newDocument()));
        try {
            writer.setNamespaceContext(namespaceContext);
        } catch (final UnsupportedOperationException e) {
            // This happens with JDK's DOM writer, which we may be using
            LOG.debug("Unable to set namespace context, falling back to setPrefix()", e);
            return new FallbackNamespaceSetter(e, prefix, uri);
        } finally {
            writer.close();
        }
        // Success, we can use setNamespaceContext()
        return new DefaultNamespaceSetter(namespaceContext);
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}