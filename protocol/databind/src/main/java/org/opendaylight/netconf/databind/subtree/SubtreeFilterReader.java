/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import java.util.ArrayList;
import java.util.Iterator;
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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeAwareEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;

@NonNullByDefault
final class SubtreeFilterReader {
    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        final var subtreeFilterBuilder = SubtreeFilter.builder(databind);
        final var context = databind.modelContext();
        // Flag that will be flipped after we find start of the first element - filter
        var isFilter = true;
        // Only checking for start of filter element - all data we interested in is inside of it
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.END_ELEMENT: {
                    // Build and return SubtreeFilter after reaching end of the filter
                    return subtreeFilterBuilder.build();
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (isFilter) {
                        // We inside filter element - skipping it because it has no use to us
                        isFilter = false;
                        break;
                    }
                    // parse all elements inside <filter> and put them into SubtreeFilter
                    fillFilter(reader, subtreeFilterBuilder, new ArrayList<>(), context,
                        getAttributes(reader, context));
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
            final List<javax.xml.namespace.QName> path, final EffectiveModelContext context,
            final List<AttributeMatch> attributes) throws XMLStreamException {
        // get info about current element
        final var qName = reader.getName();
        final var elementName = qName.getLocalPart();
        path.add(qName);
        // create NamespaceSelection based on provided info
        final var namespace = getElementNamespace(context, path);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // return containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillElement(reader, elementName, containmentBuilder, context, new ArrayList<>(path),
                        getAttributes(reader, context));
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
                    final var selection = SelectionNode.builder(namespace);
                    if (!attributes.isEmpty()) {
                        attributes.forEach(selection::add);
                    }
                    builder.add(selection.build());
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
    private static void fillElement(final XMLStreamReader reader, final String parentName,
            final ContainmentNode.Builder builder, final EffectiveModelContext context,
            final List<javax.xml.namespace.QName> path, final List<AttributeMatch> attributes)
            throws XMLStreamException {
        // get info about child element
        final var qName = reader.getName();
        // update path
        path.add(qName);
        // create NamespaceSelection based on provided info
        final var namespace = getElementNamespace(context, path);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // crete and fill the containment if there are other elements inside
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    fillElement(reader, qName.getLocalPart(), containmentBuilder, context, path, getAttributes(reader,
                        context));
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
                    if (parentName.equals(reader.getName().getLocalPart())) {
                        return;
                    }
                    // add selection if this closing element tag not related to the child element
                    final var selection = SelectionNode.builder(namespace);
                    if (!attributes.isEmpty()) {
                        attributes.forEach(selection::add);
                    }
                    builder.add(selection.build());
                    break;
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
    }

    private static List<AttributeMatch> getAttributes(final XMLStreamReader reader, final EffectiveModelContext context)
            throws XMLStreamException {
        final var childAttributes = new ArrayList<AttributeMatch>();
        final var attrCount = reader.getAttributeCount();
        for (var i = 0; i < attrCount; i++) {
            final var attrName = reader.getAttributeName(i);
            if (attrName.getNamespaceURI().isEmpty()) {
                throw new XMLStreamException("Missing namespace for attribute " + attrName);
            }
            final var exactNamespace = getAttributeNamespace(context, attrName);
            final var attrValue = reader.getAttributeValue(i);
            childAttributes.add(new AttributeMatch(exactNamespace, attrValue));
        }
        return childAttributes;
    }

    private static Exact getAttributeNamespace(final EffectiveModelContext context,
            final javax.xml.namespace.QName attrName) throws XMLStreamException {
        final var attrNamespace = attrName.getNamespaceURI();
        // lookup module by namespace
        final var it = context.findModuleStatements(XMLNamespace.of(attrNamespace)).iterator();
        if (it.hasNext()) {
            return new Exact(NodeIdentifier.create(QName.create(it.next().localQNameModule(),
                attrName.getLocalPart())));
        } else {
            throw new XMLStreamException("Failed to lookup module schema for namespace "
                + attrNamespace);
        }
    }

    private static NamespaceSelection getElementNamespace(final EffectiveModelContext context,
            final List<javax.xml.namespace.QName> path)
            throws XMLStreamException {
        final var firstNode = path.getFirst();
        final var firstNamespace = firstNode.getNamespaceURI();
        final var modules = new ArrayList<ModuleEffectiveStatement>();
        final var nodeQNames = new ArrayList<QName>();
        // check path nodes to determine what we are dealing with - exact namespace, wildcard or mix
        if (!firstNamespace.isEmpty()) {
            // try to find exact module if we know namespace of the first node
            final var found = context.findModuleStatements(XMLNamespace.of(firstNamespace)).iterator();
            if (found.hasNext()) {
                final var module = found.next();
                // if last element has namespace then assume all elements above have too and if we found module then
                // just create exact namespace selection directly and return it
                final var lastNode = path.getLast();
                if (!lastNode.getNamespaceURI().isEmpty()) {
                    // go through schema tree using path elements to find node
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
                throw new XMLStreamException("Failed to lookup module with namespace %s." + firstNamespace);
            }
        } else {
            // in this case wildcard starts from the first element, so we need to check all modules and try to narrow
            // search scope by finding every module that contain child node with same name as first node in path
            for (final var module : context.getModuleStatements().values()) {
                final var found = module.schemaTreeNodes().stream().anyMatch(schemaTreeEffectiveStatement -> firstNode
                    .getLocalPart().equals(schemaTreeEffectiveStatement.getDeclared().argument().getLocalName()));
                if (found) {
                    modules.add(module);
                }
            }
        }
        // process wildcard for selected modules
        for (final var module : modules) {
            // if true then one or few top elements of the filter has specified namespace means they can be treated same
            // as exact namespace selection
            final var it = path.iterator();
            // take first node and check if it has namespace
            var qname = it.next();
            while (it.hasNext()) {
                final var nodeNamespace = qname.getNamespaceURI();
                if (!nodeNamespace.isEmpty()) {
                    // lookup module for every node in case filter has multiple namespaces
                    final var nodeModule = context.findModuleStatements(XMLNamespace.of(nodeNamespace)).iterator();
                    if (nodeModule.hasNext()) {
                        nodeQNames.add(QName.create(nodeModule.next().localQNameModule(), qname.getLocalPart()));
                    } else {
                        throw new XMLStreamException("Failed to lookup module schema for namespace "
                            + nodeNamespace);
                    }
                    qname = it.next();
                } else {
                    // no more filter elements that has specified namespace
                    break;
                }
            }
            final var firstWildcard = qname;
            final List<SchemaTreeEffectiveStatement<?>> wildcardChildren;
            // if there were some nodes with namespace specified then start search from that point
            if (!nodeQNames.isEmpty()) {
                // skip to the place where namespaces end and wildcard starts
                final var found = module.findSchemaTreeNode(nodeQNames)
                    .orElseThrow(() -> new XMLStreamException("Failed to lookup node schema with name "
                        + nodeQNames.getLast().getLocalName()));
                // find all children that has same name as wildcard
                wildcardChildren = found instanceof SchemaTreeAwareEffectiveStatement<?,?> treeAware
                    ? treeAware.schemaTreeNodes().stream().filter(child -> firstWildcard.getLocalPart().equals(child
                        .getDeclared().argument().getLocalName())).toList() : null;
            } else {
                // in this case whole filter node is a wildcard so start from searching
                wildcardChildren = module.schemaTreeNodes().stream().filter(schemaTreeEffectiveStatement ->
                    firstWildcard.getLocalPart().equals(schemaTreeEffectiveStatement.getDeclared().argument()
                        .getLocalName())).toList();
            }
            if (wildcardChildren == null || wildcardChildren.isEmpty()) {
                throw new XMLStreamException("Failed to lookup node schema for " + firstWildcard.getLocalPart());
            }
            // process children recursively
            final var unqualified = UnresolvedQName.Unqualified.of(path.getLast().getLocalPart());
            final var qNames = processChildrenStatements(wildcardChildren, it);
            if (qNames.isEmpty()) {
                throw new XMLStreamException("Failed to identifiers for " + unqualified.getLocalName());
            }
            final var identifiers = qNames.stream().map(NodeIdentifier::create).toList();
            return new NamespaceSelection.Wildcard(unqualified, identifiers);
        }
        throw new XMLStreamException("Failed to lookup any module with that corresponds to filter");
    }

    private static List<QName> processChildrenStatements(final List<SchemaTreeEffectiveStatement<?>> found,
            final Iterator<javax.xml.namespace.QName> it) {
        if (it.hasNext()) {
            // if true then we have children after current node means it is too early to collect QNames
            final var childName = it.next().getLocalPart();
            final var qNames = new ArrayList<QName>();
            for (final var stmt : found) {
                final var children = stmt instanceof SchemaTreeAwareEffectiveStatement<?,?> treeAware
                    ? treeAware.schemaTreeNodes().stream().filter(child -> childName.equals(child.getDeclared()
                    .argument().getLocalName())).toList() : null;
                if (children == null || children.isEmpty()) {
                    continue;
                }
                qNames.addAll(processChildrenStatements(children, it));
            }
            return qNames;
        } else {
            // current node is the lase one - time to collect QNames and return them
            final var qNames = new ArrayList<QName>();
            for (final var stmt : found) {
                qNames.add(stmt.getDeclared().argument());
            }
            return qNames;
        }
    }
}
