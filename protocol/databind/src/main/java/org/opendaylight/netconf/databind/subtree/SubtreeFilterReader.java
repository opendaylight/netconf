/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;

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
                    final var qName = reader.getName();
                    // TODO questionable
                    final var elementNamespace = qName.getNamespaceURI();
                    final var module = databind.modelContext().findModules(XMLNamespace.of(elementNamespace)).stream()
                        .findAny().orElseThrow(() -> new XMLStreamException("No corresponding module to the filter"));
                    subtreeFilterBuilder.add(fillChildren(reader, module));
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

    private static Sibling fillChildren(final XMLStreamReader reader, final Module module)
            throws XMLStreamException {
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        final var elementNamespace = qName.getNamespaceURI();
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // return containment if there are other elements inside
                    final var namespace = getNamespaceSelection(module, elementNamespace, elementName);
                    return ContainmentNode.builder(namespace).add(fillChildren(reader, module)).build();
                }
                case XMLStreamConstants.CHARACTERS: {
                    // return content if there are non whitespace characters
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        return new ContentMatchNode(getNamespaceSelection(module, elementNamespace, elementName), text);
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // return selection if this is just closing element tag
                    return SelectionNode.builder(getNamespaceSelection(module, elementNamespace, elementName)).build();
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        return null;
    }

    private static NamespaceSelection getNamespaceSelection(final Module module, final String elementNamespace,
            final String elementName) {
        final NamespaceSelection namespace;
        if (!elementNamespace.isEmpty()) {
            final QName qName;
            if (module.getRevision().isPresent()) {
                qName = QName.create(elementNamespace, elementName, module.getRevision().get());
            } else {
                qName = QName.create(elementNamespace, elementName);
            }
            namespace = new Exact(NodeIdentifier.create(qName));
        } else {
            final var unqualified = UnresolvedQName.Unqualified.of(elementName);
            // TODO find all matching nodes
//            final var identifiers = module.getChildNodes().stream()
//                    .allMatch(dataSchemaNode -> dataSchemaNode.getQName().getLocalName().equals(elementName));
            namespace = new NamespaceSelection.Wildcard(unqualified, List.of());
        }
        return namespace;
    }
}
