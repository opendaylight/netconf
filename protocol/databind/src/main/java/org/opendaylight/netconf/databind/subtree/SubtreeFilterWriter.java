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

@NonNullByDefault
final class SubtreeFilterWriter {
    SubtreeFilterWriter() {
        // Hidden on purpose
    }

    static void writeSubtreeFilter(final XMLStreamWriter writer, final SubtreeFilter filter) throws XMLStreamException {
        final var prefixes = Prefixes.of(filter);
        writeSiblings(writer, prefixes, filter);
    }

    private static void writeSiblings(final XMLStreamWriter writer, final Prefixes prefixes, final SiblingSet siblings)
        throws XMLStreamException {
        for (var content : siblings.contentMatches()) {
            writeContentMatch(writer, prefixes, content);
        }

        for (var selection : siblings.selections()) {
            writeSelection(writer, prefixes, selection);
        }

        for (var containment : siblings.containments()) {
            writeContainment(writer, prefixes, containment);
        }
    }

    private static void writeContentMatch(final XMLStreamWriter writer, final Prefixes prefixes,
        final ContentMatchNode content) throws XMLStreamException {
        switch (content.selection()) {
            case NamespaceSelection.Exact(var qName) -> {
                final var prefix = prefixes.lookUpPrefix(qName.getNamespace().toString());
                writer.writeStartElement(prefix, qName.getLocalName(), qName.getNamespace().toString());
            }
            case NamespaceSelection.Wildcard(var name, var qNames) -> writer.writeStartElement(name.getLocalName());
        }
        writer.writeCharacters(String.valueOf(content.value()));
        writer.writeEndElement();
    }

    private static void writeSelection(final XMLStreamWriter writer, final Prefixes prefixes,
        final SelectionNode selection) throws XMLStreamException {
        switch (selection.selection()) {
            case NamespaceSelection.Exact(var qName) -> {
                final var prefix = prefixes.lookUpPrefix(qName.getNamespace().toString());
                writer.writeStartElement(prefix, qName.getLocalName(), qName.getNamespace().toString());
            }
            case NamespaceSelection.Wildcard(var name, var qNames) -> writer.writeStartElement(name.getLocalName());
        }

        for (var attr : selection.attributeMatches()) {
            final var sel = attr.selection();
            final var prefix = prefixes.lookUpPrefix(sel.qname().getNamespace().toString());
            writer.writeAttribute(prefix, sel.qname().getNamespace().toString(), sel.qname().getLocalName(),
                attr.value().toString());
        }

        writer.writeEndElement();
    }

    private static void writeContainment(final XMLStreamWriter writer, final Prefixes prefixes,
        final ContainmentNode containment) throws XMLStreamException {
        switch (containment.selection()) {
            case NamespaceSelection.Exact(var qName) -> {
                final var prefix = prefixes.lookUpPrefix(qName.getNamespace().toString());
                writer.writeStartElement(prefix, qName.getLocalName(), qName.getNamespace().toString());
            }
            case NamespaceSelection.Wildcard(var qName, var ignored) -> writer.writeStartElement(qName.getLocalName());
        }

        writeSiblings(writer, prefixes, containment);
        writer.writeEndElement();
    }

}
