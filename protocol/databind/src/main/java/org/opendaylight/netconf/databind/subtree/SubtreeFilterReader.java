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
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

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
                    final var inferenceStack = SchemaInferenceStack.of(databind.modelContext());
                    fillFilter(reader, subtreeFilterBuilder, inferenceStack);
                    break;
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        throw new XMLStreamException("Unexpected end of the document");
    }

    // Goes over elements inside of filter node itself and fills SubtreeFilter builder with respective sibling types
    private static void fillFilter(final XMLStreamReader reader, final SubtreeFilter.Builder builder,
            final SchemaInferenceStack inferenceStack) throws XMLStreamException {
        // get info about current element
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        final var elementNamespace = qName.getNamespaceURI();
        // create NamespaceSelection based on provided info
        final var namespace = getNamespaceSelection(inferenceStack, elementNamespace, elementName);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // return containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillContainment(reader, elementName, containmentBuilder, inferenceStack);
                    builder.add(containmentBuilder.build());
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    // return content if there are non whitespace characters
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        if (!elementNamespace.isEmpty()) {
                            inferenceStack.exit();
                        }
                        builder.add(new ContentMatchNode(namespace, text));
                        return;
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // return selection if this is just closing element tag
                    if (!elementNamespace.isEmpty()) {
                        inferenceStack.exit();
                    }
                    builder.add(SelectionNode.builder(namespace).build());
                    return;
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
    }

    // Goes over elements inside of containment node and fills builder with respective sibling types
    private static void fillContainment(final XMLStreamReader reader, final String containmentName,
            final ContainmentNode.Builder builder, final SchemaInferenceStack inferenceStack)
            throws XMLStreamException {
        // get info about child element
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        final var elementNamespace = qName.getNamespaceURI();
        // create NamespaceSelection based on provided info
        final var namespace = getNamespaceSelection(inferenceStack, elementNamespace, elementName);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // crete and fill the containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillContainment(reader, elementName, containmentBuilder, inferenceStack);
                    builder.add(containmentBuilder.build());
                    break;
                }
                case XMLStreamConstants.CHARACTERS: {
                    // check text inside the node
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        // add content if there are non whitespace characters
                        builder.add(new ContentMatchNode(namespace, text));
                        // exit from content node
                        if (!elementNamespace.isEmpty()) {
                            inferenceStack.exit();
                        }
                        // skip end of the content node
                        reader.next();
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // exit from node that ended
                    if (!elementNamespace.isEmpty()) {
                        inferenceStack.exit();
                    }
                    // If true then we reached end of the containment
                    if (containmentName.equals(reader.getName().getLocalPart())) {
                        return;
                    }
                    // return selection if this closing element tag not related to the containment
                    builder.add(SelectionNode.builder(namespace).build());
                    break;
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
    }

    private static NamespaceSelection getNamespaceSelection(final SchemaInferenceStack inferenceStack,
            final String elementNamespace, final String elementName) throws XMLStreamException {
        final var context = inferenceStack.modelContext();
        final NamespaceSelection namespace;
        if (!elementNamespace.isEmpty()) {
            // find module by namespace if we have it
            final var it = context.findModuleStatements(XMLNamespace.of(elementNamespace)).iterator();
            if (it.hasNext()) {
                final var module = it.next();
                // try enter
                inferenceStack.enterSchemaTree(QName.create(module.localQNameModule(), elementName));
                // TODO check augmentations?
                final var found = module.findSchemaTreeNode(inferenceStack.toSchemaNodeIdentifier())
                    .orElseThrow(() -> new XMLStreamException(
                        String.format("Failed to lookup  node schema with name %s", elementName)));
                namespace = new Exact(NodeIdentifier.create(found.argument()));
            } else {
                throw new XMLStreamException(String.format("Failed to lookup module with namespace %s.",
                    elementNamespace));
            }
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
