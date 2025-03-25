/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.yangtools.yang.common.QName;

@NonNullByDefault
final class SubtreeFilterReader {
    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        final var subtreeFilterBuilder = SubtreeFilter.builder(databind);
        // Flag that will be flipped after we find start of the first element - filter
        var isFilter = true;
        // Only checking for start of filter element - all data we interested in is inside of it
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.END_DOCUMENT: {
                    // Build and return SubtreeFilter after reaching end of the document
                    return subtreeFilterBuilder.build();
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (isFilter) {
                        // We inside filter element - skipping it because it has no use to us
                        isFilter = false;
                        break;
                    }
                    subtreeFilterBuilder.add(fillChildren(reader));
                    break;
                }
//                case XMLStreamConstants.ATTRIBUTE: {
//                    final var attrs = reader.getAttributeCount();
//                    for (var i = 0; i < attrs; i++) {
//                        final var attrName = reader.getAttributeName(i);
//                        final var attrValue = reader.getAttributeValue(i);
//                    }
//                    break;
//                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        throw new XMLStreamException("Unexpected end of the document");
    }

    private static Sibling fillChildren(final XMLStreamReader reader)
            throws XMLStreamException {
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        final var elementNamespace = qName.getNamespaceURI();
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // TODO handle wildcard
                    ContainmentNode.builder(new Exact(QName.create(elementNamespace, elementName)));
                    fillChildren(reader);
                    break;
                }
                case XMLStreamConstants.CHARACTERS: {
                    // TODO handle content
                    final var text = reader.getTextCharacters();
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    return SelectionNode.builder(new Exact(QName.create(elementNamespace, elementName))).build();
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        return null;
    }
}
