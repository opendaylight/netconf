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
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;
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

    /**
     * Read xml peeling off wrapper around filter elements(usually something like <filter type="subtree"><filter/>) to
     * get to elements with filter data and construct subtree filter based those elements
     *
     * @param reader reader that goes through xml elements
     * @param databind context
     * @return filter that was created based on elements of xml
     * @throws XMLStreamException when encountering issues with xml structure
     */
    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        final var subtreeFilterBuilder = SubtreeFilter.builder(databind);
        final var context = databind.modelContext();
        // pelee filter wrapper(for example <filter type="subtree"><filter/>) to access elements with data
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                break;
            }
        }
        // start processing filter elements
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                fillElement(reader, subtreeFilterBuilder, new ArrayList<>(), context, getAttributes(reader, context));
                break;
            }
        }
        return subtreeFilterBuilder.build();
    }

    /**
     * Fills element builder with sibling nodes that represent its child elements. If child element have children of its
     * own then fills them recursively.
     *
     * @param reader reader that goes through xml elements
     * @param builder builder that will collect all sibling nodes that was processed
     * @param path list of element names that represent path to the element
     * @param context model context
     * @param attributes element attributes
     * @throws XMLStreamException when encountering issues with xml structure
     */
    private static void fillElement(final XMLStreamReader reader, final SiblingSetBuilder builder,
            final List<javax.xml.namespace.QName> path, final EffectiveModelContext context,
            final List<AttributeMatch> attributes) throws XMLStreamException {
        // get element and update path
        final var elementName = reader.getName();
        path.add(elementName);
        // create NamespaceSelection based on provided info
        final var namespace = getElementNamespace(context, path);
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // new element starts before previous ended means this is child element of containment
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    // add first sibling since we already found it
                    fillElement(reader, containmentBuilder, path, context, getAttributes(reader, context));
                    // add rest of the siblings until we reach the end of the element
                    while (reader.hasNext()) {
                        final var event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            fillElement(reader, containmentBuilder, path, context, getAttributes(reader, context));
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            // if true then we reached the end of the containment element
                            if (elementName.equals(reader.getName())) {
                                break;
                            }
                        }
                    }
                    // build containment and add it to parent siblings
                    builder.addSibling(containmentBuilder.build());
                    // remove last step from the path and return step above
                    path.removeLast();
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    final var text = reader.getText();
                    // check if there are non whitespace characters in text
                    if (!text.trim().isEmpty()) {
                        // create content match if there are some data inside
                        // FIXME parse text and create object based on the node type in model context
                        builder.addSibling(new ContentMatchNode(namespace, text));
                        // remove last step from the path and return step above
                        path.removeLast();
                        return;
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // end element right after start means this is empty element - create selection
                    final var selection = SelectionNode.builder(namespace);
                    // add attributes if there are some
                    if (!attributes.isEmpty()) {
                        attributes.forEach(selection::add);
                    }
                    builder.addSibling(selection.build());
                    // remove last step from the path and return step above
                    path.removeLast();
                    return;
                }
                default: {
                    // ignore all other events
                    break;
                }
            }
        }
        throw new XMLStreamException("Unexpected end of the document");
    }

    /**
     * Checks if xml element has any attributes and if so - collects them into {@link AttributeMatch} list.
     *
     * @param reader reader that goes through xml elements
     * @param context model context
     * @return list of the attributes
     * @throws XMLStreamException when encountering issues with xml structure
     */
    private static List<AttributeMatch> getAttributes(final XMLStreamReader reader, final EffectiveModelContext context)
            throws XMLStreamException {
        final var childAttributes = new ArrayList<AttributeMatch>();
        final var attrCount = reader.getAttributeCount();
        // go through attributes
        for (var i = 0; i < attrCount; i++) {
            final var attrName = reader.getAttributeName(i);
            // attribute must have some namespace
            if (attrName.getNamespaceURI().isEmpty()) {
                throw new XMLStreamException("Missing namespace for attribute " + attrName);
            }
            final var attrNamespace = attrName.getNamespaceURI();
            // lookup module by namespace
            final var it = context.findModuleStatements(XMLNamespace.of(attrNamespace)).iterator();
            final Exact exactNamespace;
            if (it.hasNext()) {
                exactNamespace = new Exact(NodeIdentifier.create(QName.create(it.next().localQNameModule(),
                    attrName.getLocalPart())));
            } else {
                throw new XMLStreamException("Failed to lookup module schema for namespace "
                    + attrNamespace);
            }
            // FIXME parse text and create object value from attribute text
            final var attrValue = reader.getAttributeValue(i);
            childAttributes.add(new AttributeMatch(exactNamespace, attrValue));
        }
        return childAttributes;
    }

    /**
     * Goes through model context using path until finds last element and creates {@link NamespaceSelection} for it.
     *
     * @param context model context
     * @param path list of element names that represent path to the element
     * @return {@link Exact} or {@link Wildcard} namespace for the element
     * @throws XMLStreamException when encountering issues with xml structure
     */
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
            return new Wildcard(unqualified, identifiers);
        }
        throw new XMLStreamException("Failed to lookup any module with that corresponds to filter");
    }

    /**
     * This method goes down the schema tree recursively searching for every element that has matching name with current
     * element of path iterator. Upon reaching the end of the path collects every matching {@link QName} to list.
     *
     * @param found list of statements where
     * @param it iterator that goes through path elements
     * @return list of the {@link QName}s that have same local name as element in the end of the path
     */
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
            // current node is the last one - time to collect QNames and return them
            final var qNames = new ArrayList<QName>();
            for (final var stmt : found) {
                qNames.add(stmt.getDeclared().argument());
            }
            return qNames;
        }
    }
}
