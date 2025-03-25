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

@NonNullByDefault
final class SubtreeFilterReader {
    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        final var subtreeFilterBuilder = SubtreeFilter.builder(databind);
        // TODO skip filter element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    break;
                }
                case XMLStreamConstants.END_DOCUMENT: {
                    return subtreeFilterBuilder.build();
                }
                case XMLStreamConstants.START_ELEMENT: {
                    final var qName = reader.getName();
                    final var elementName = qName.getLocalPart();
                    final var elementNamespace = qName.getNamespaceURI();
                    // parse NS declarations
//                    int nsCnt = reader.getNamespaceCount();
                    // TODO find a way to check children and know what type of element this is
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // TODO extract this into recursion
                    break;
                }
                case XMLStreamConstants.NAMESPACE: {
                    // TODO handle many namespaces
                    break;
                }
                case XMLStreamConstants.CHARACTERS: {
                    final var text = reader.getTextCharacters();
                    // TODO smth strange here
                    break;
                }
                case XMLStreamConstants.SPACE: {
                    // probably just ignore
                    break;
                }
                case XMLStreamConstants.ATTRIBUTE: {
                    // TODO
                    final var attrs = reader.getAttributeCount();
                    for (var i = 0; i < attrs; i++) {
                        final var attrName = reader.getAttributeName(i);
                        final var attrValue = reader.getAttributeValue(i);
                    }
                    break;
                }
                case XMLStreamConstants.PROCESSING_INSTRUCTION: {
                    // probably just ignore
                    break;
                }
                case XMLStreamConstants.COMMENT: {
                    // probably just ignore
                    break;
                }
                case XMLStreamConstants.DTD: {
                    // probably just ignore
                    break;
                }
                case XMLStreamConstants.ENTITY_REFERENCE: {
                    // probably just ignore
                    break;
                } // TODO collect all data that ignored into one place
                default: {
                    throw new UnsupportedOperationException();
                }
            }
        }
        throw new UnsupportedOperationException();
    }
}
