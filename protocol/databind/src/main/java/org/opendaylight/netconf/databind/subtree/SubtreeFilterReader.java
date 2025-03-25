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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@NonNullByDefault
final class SubtreeFilterReader {
    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        final var inferenceStack = SchemaInferenceStack.of(databind.modelContext());
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
//                    final var qName = reader.getName();
//                    final var elementNamespace = qName.getNamespaceURI();
//                    final org.opendaylight.yangtools.yang.model.api.Module module;
//                    if (!elementNamespace.isEmpty()) {
//                        module = databind.modelContext().findModule(XMLNamespace.of(elementNamespace)).orElseThrow();
//                    }
                    // TODO walk through context
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
                    // return containment if there are other elements inside
                    return ContainmentNode.builder(exactNamespace(elementNamespace, elementName))
                        .add(fillChildren(reader)).build();
                }
                case XMLStreamConstants.CHARACTERS: {
                    // return content if there are non whitespace characters
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        return new ContentMatchNode(exactNamespace(elementNamespace, elementName), text);
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // return selection if this is just closing element tag
                    return SelectionNode.builder(exactNamespace(elementNamespace, elementName)).build();
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        return null;
    }

    private static NamespaceSelection exactNamespace(final @Nullable String namespace, final String name) {
        // TODO replace with external QName or List<QName> depending on whether we have wildcard or not
        return new Exact(YangInstanceIdentifier.NodeIdentifier.create(QName.create(namespace, name)));
    }
}
