/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import java.util.ArrayList;
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
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
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
                    fillFilter(reader, subtreeFilterBuilder, new ArrayList<>(), databind);
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
            final List<javax.xml.namespace.QName> path, final DatabindContext databind)
            throws XMLStreamException {
        // get info about current element
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        path.add(qName);
        // create NamespaceSelection based on provided info
        final var namespace = getNamespaceSelection(databind, path);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // return containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillContainment(reader, elementName, containmentBuilder, databind, new ArrayList<>(path));
                    builder.add(containmentBuilder.build());
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    // return content if there are non whitespace characters
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        builder.add(new ContentMatchNode(namespace, text));
                        return;
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // return selection if this is just closing element tag
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
            final ContainmentNode.Builder builder, final DatabindContext databind,
            final List<javax.xml.namespace.QName> path) throws XMLStreamException {
        // get info about child element
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        path.add(qName);
        // create NamespaceSelection based on provided info
        final var namespace = getNamespaceSelection(databind, path);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // crete and fill the containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillContainment(reader, elementName, containmentBuilder, databind, path);
                    builder.add(containmentBuilder.build());
                    break;
                }
                case XMLStreamConstants.CHARACTERS: {
                    // check text inside the node
                    final var text = reader.getText();
                    if (!text.trim().isEmpty()) {
                        // add content if there are non whitespace characters
                        builder.add(new ContentMatchNode(namespace, text));
                        // skip end of the content node
                        reader.next();
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
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

    private static NamespaceSelection getNamespaceSelection(final DatabindContext databind,
            final List<javax.xml.namespace.QName> path)
            throws XMLStreamException {
        final var context = databind.modelContext();
        final var firstNode = path.getFirst();
        final var firstNamespace = firstNode.getNamespaceURI();
        final var modules = new ArrayList<ModuleEffectiveStatement>();
        // check path nodes to determine what we are dealing with - exact namespace of wildcard
        if (!firstNode.getNamespaceURI().isEmpty()) {
            // try to find exact module if we know namespace of the first node
            final var found = context.findModuleStatements(XMLNamespace.of(firstNamespace)).iterator();
            if (found.hasNext()) {
                final var module = found.next();
                // if last element has namespace then assume all elements above have too and if we found module then
                // just create exact namespace selection directly and return it
                final var lastNode = path.getLast();
                if (!lastNode.getNamespaceURI().isEmpty()) {
                    final var inf = SchemaInferenceStack.of(context);
                    final var it = path.iterator();
                    final var root = it.next();
                    // go through schema tree using path elements to find node
                    final var nodeQNames = new ArrayList<QName>();
                    for (final var qname: path) {
                        final var nodeNamespace = qname.getNamespaceURI();
                        if (!nodeNamespace.isEmpty()) {
                            // lookup module for every node in case filter has multiple namespaces
                            final var nodeModule = context.findModuleStatements(XMLNamespace.of(nodeNamespace))
                                .iterator();
                            if (nodeModule.hasNext()) {
                                nodeQNames.add(QName.create(nodeModule.next().localQNameModule(),
                                    qname.getLocalPart()));
                            } else {
                                throw new XMLStreamException("Failed to lookup module schema for namespace "
                                    + nodeNamespace);
                            }
                        } else {
                            throw new XMLStreamException("Unexpected wildcard in the middle of the filter");
                        }
                    }
                    final var foundNode = module.findSchemaTreeNode(nodeQNames)
                        .orElseThrow(() -> new XMLStreamException("Failed to lookup node schema with name "
                            + lastNode.getLocalPart()));
                    return new Exact(NodeIdentifier.create(foundNode.argument()));
                }
                // if last element doesn't have namespace then we have a wildcard somewhere in the filter node but not
                // at the start, so we can start creating namespace selection from module that was already found
                modules.add(module);
            } else {
                throw new XMLStreamException(String.format("Failed to lookup module with namespace %s.",
                    firstNamespace));
            }
        } else {
            // in this case wildcard starts from the first element, so we need to check all modules and try to narrow
            // search scope by finding every module that contain child node with same name as first node in path
            for(final var module : context.getModuleStatements().values()) {
                final var found = module.findSchemaTreeNode(QName.create(module.localQNameModule(),
                    firstNode.getLocalPart()));
                if (found.isPresent()) {
                    modules.add(module);
                }
            }
        }
        final var unqualified = UnresolvedQName.Unqualified.of(path.getLast().getLocalPart());
        // TODO resolve wildcard
//        final var nodeQNames = new ArrayList<QName>();
        // process path and lookup corresponding structures in modules
//        for (final var module : modules) {
//            final var revision = module.localQNameModule().revision();
//            final var inf = SchemaInferenceStack.of(context);
//            for (final var qname: path) {
//                final var nodeNamespace = XMLNamespace.of(qname.getNamespaceURI());
//                if (!elementNamespace.isEmpty()) {
//                    inf.enterSchemaTree(QName.create(nodeNamespace, revision, qname.getLocalPart()));
//                } else {
//
//                }
//            }
//            final var found = module.findSchemaTreeNode(inf.toSchemaNodeIdentifier())
//                .orElseThrow(() -> new XMLStreamException(
//                    String.format("Failed to lookup node schema with name %s", elementName)));
//            nodeQNames.add(found.argument());
//        }
        return new NamespaceSelection.Wildcard(unqualified, List.of());
    }
}
