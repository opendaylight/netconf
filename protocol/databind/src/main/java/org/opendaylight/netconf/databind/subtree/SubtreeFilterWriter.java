/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;

@NonNullByDefault
final class SubtreeFilterWriter {
    private SubtreeFilterWriter() {
        // Hidden on purpose
    }

    static void writeSubtreeFilter(final XMLStreamWriter writer, final SubtreeFilter filter) throws XMLStreamException {
        final var prefixes = Prefixes.of(filter);
        // Write <filter type="subtree"> element
        writer.writeStartElement("filter");
        writer.writeAttribute("type", "subtree");

        // Declare all namespaces
        for (final var entry : prefixes.getNamespaceToPrefixMap().entrySet()) {
            writer.setPrefix(entry.getValue(), entry.getKey());
            writer.writeNamespace(entry.getValue(), entry.getKey());
        }

        writeSiblings(writer, prefixes, filter);

        writer.writeEndElement(); // </filter>
    }

    private static void writeSiblings(final XMLStreamWriter writer, final Prefixes prefixes, final SiblingSet siblings)
            throws XMLStreamException {
        for (final var content : siblings.contentMatches()) {
            writeContentMatch(writer, prefixes, content);
        }

        for (final var selection : siblings.selections()) {
            writeSelection(writer, prefixes, selection);
        }

        for (final var containment : siblings.containments()) {
            writeContainment(writer, prefixes, containment);
        }
    }

    private static void writeContentMatch(final XMLStreamWriter writer, final Prefixes prefixes,
            final ContentMatchNode content) throws XMLStreamException {
        switch (content.selection()) {
            case Exact(var qname) -> {
                final var prefix = prefixes.lookUpPrefix(qname.getNamespace().toString());
                writer.writeStartElement(prefix, qname.getLocalName(),
                    qname.getNamespace().toString());
            }
            case Wildcard wildcard -> writer.writeStartElement(wildcard.name().getLocalName());
        }
        writer.writeCharacters(String.valueOf(content.qnameValueMap().values().stream().findFirst().orElseThrow()));
        writer.writeEndElement();
    }

    private static void writeSelection(final XMLStreamWriter writer, final Prefixes prefixes,
            final SelectionNode selection) throws XMLStreamException {
        switch (selection.selection()) {
            case Exact(var qname) -> {
                final var prefix = prefixes.lookUpPrefix(qname.getNamespace().toString());
                writer.writeStartElement(prefix, qname.getLocalName(),
                    qname.getNamespace().toString());
            }
            case Wildcard wildcard -> writer.writeStartElement(wildcard.name().getLocalName());
        }

        for (var attr : selection.attributeMatches()) {
            final var sel = attr.selection();
            final var prefix = prefixes.lookUpPrefix(sel.qname().getNamespace().toString());
            writer.writeAttribute(prefix, sel.qname().getNamespace().toString(),
                sel.qname().getLocalName(),
                attr.value().toString());
        }

        writer.writeEndElement();
    }

    private static void writeContainment(final XMLStreamWriter writer, final Prefixes prefixes,
            final ContainmentNode containment) throws XMLStreamException {
        switch (containment.selection()) {
            case Exact(var qname) -> {
                final var prefix = prefixes.lookUpPrefix(qname.getNamespace().toString());
                writer.writeStartElement(prefix, qname.getLocalName(),
                    qname.getNamespace().toString());
            }
            case Wildcard wildcard -> writer.writeStartElement(wildcard.name().getLocalName());
        }

        writeSiblings(writer, prefixes, containment);
        writer.writeEndElement();
    }
}
