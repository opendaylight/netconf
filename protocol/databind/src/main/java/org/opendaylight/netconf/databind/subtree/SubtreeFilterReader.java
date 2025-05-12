/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javanet.staxutils.SimpleNamespaceContext;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubtreeFilterReader {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilterReader.class);

    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    /**
     * Read xml peeling off wrapper around filter elements(usually something like
     * {@code <filter type="subtree">}) to get to elements with filter data and construct subtree filter based
     * those elements.
     *
     * @param reader reader that goes through xml elements
     * @param databind context
     * @return filter that was created based on elements of xml
     * @throws XMLStreamException when encountering issues with xml structure
     */
    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException {
        // pelee filter wrapper(for example <filter type="subtree"></filter>) to access elements with data
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                break;
            }
        }
        final var subtreeFilterBuilder = SubtreeFilter.builder(databind);
        final var context = databind.modelContext();
        final var codec = databind.xmlCodecs();
        // create namespace context to parse content using XmlCodecs
        final var prefixToNs = new HashMap<String, String>();
        for (final var module : context.getModuleStatements().values()) {
            for (final var prefix : module.namespacePrefixes()) {
                prefixToNs.putIfAbsent(prefix.getValue(), prefix.getKey().namespace().toString());
            }
        }
        final var nsContext = new SimpleNamespaceContext(prefixToNs);

        final var stack = SchemaInferenceStack.of(context);
        // start processing filter elements
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                fillElement(reader, subtreeFilterBuilder, List.of(stack), context, getAttributes(reader, context),
                    codec, nsContext);
                break;
            }
        }
        return subtreeFilterBuilder.build();
    }

    /**
     * This method goes through XML elements that came from {@link XMLStreamReader} and looking for these elements
     * inside the model context. Based on this data it creates sibling nodes and adding them into builder to form
     * {@link SubtreeFilter} structure. If element have children of its own then fills them the same way recursively.
     *
     * @param reader reader that goes through xml elements
     * @param builder builder that will collect all sibling nodes that was processed
     * @param parents map of path {@link SchemaInferenceStack} and node context {@link DataSchemaContext} that point to
     *                parent/s of child that is being processed
     * @param context model context
     * @param attributes element attributes
     * @param codec xml codec used for content parsing
     * @param nsContext context that store prefix to namespace map of the model
     * @throws XMLStreamException when encountering issues with xml structure
     */
    private static void fillElement(final XMLStreamReader reader, final SiblingSetBuilder builder,
            final List<SchemaInferenceStack> parents, final EffectiveModelContext context,
            final List<AttributeMatch> attributes, final XmlCodecFactory codec, final NamespaceContext nsContext)
            throws XMLStreamException {
        final var elementName = reader.getName();
        final var elementLocalName = elementName.getLocalPart();
        final var elementNamespace = elementName.getNamespaceURI();
        final var childrenQNames = new ArrayList<QName>();
        final var children = new ArrayList<SchemaInferenceStack>();
        LOG.debug("Starting processing child node {}.", elementName);
        for (final var parentStack : parents) {
            if (elementNamespace.isEmpty()) {
                LOG.debug("Processing {} node as wildcard.", elementName);
                // if we don't have namespace to search for exact match then we have to search for child nodes with
                // every possible namespace because children not necessarily have same namespace as parent
                for (final var module : context.getModuleStatements().values()) {
                    findNodeInModule(elementLocalName, module, parentStack, children, childrenQNames);
                }
            } else {
                LOG.debug("Processing {} node as exact.", elementName);
                // try to find exact module if we know namespace of the node
                final var childModule = context.findModuleStatements(XMLNamespace.of(elementNamespace)).iterator();
                if (childModule.hasNext()) {
                    findNodeInModule(elementLocalName, childModule.next(), parentStack, children, childrenQNames);
                } else {
                    throw new XMLStreamException("Failed to lookup module with namespace %s."
                        .formatted(elementNamespace));
                }
            }
        }
        final NamespaceSelection namespace;
        if (!childrenQNames.isEmpty()) {
            if (elementNamespace.isEmpty()) {
                LOG.debug("Creating Wildcard NamespaceSelection for {} node.", elementName);
                namespace = new Wildcard(UnresolvedQName.Unqualified.of(elementLocalName), childrenQNames.stream()
                    .sorted().toList());
            } else {
                // there have to be only one qname in the list if we have namespace
                LOG.debug("Creating Exact NamespaceSelection for {} node.", elementName);
                namespace = new Exact(childrenQNames.getFirst());
            }
        } else {
            throw new XMLStreamException("Failed to lookup node with name %s in schema context."
                .formatted(elementLocalName));
        }
        // Check what is next event after start of the element
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT: {
                    // new element starts before previous ended means this is child element of containment
                    LOG.debug("Starting processing children of the {} node.", elementName);
                    final var containmentBuilder = ContainmentNode.builder(namespace);
                    // add first child since we already found it
                    fillElement(reader, containmentBuilder, children, context, getAttributes(reader, context), codec,
                        nsContext);
                    // add rest of the children until we reach the end of the element
                    while (reader.hasNext()) {
                        final var event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            fillElement(reader, containmentBuilder, children, context, getAttributes(reader, context),
                                codec, nsContext);
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            // if true then we reached the end of the containment element
                            if (elementName.equals(reader.getName())) {
                                LOG.debug("Reached end of {} node. Returning log level above.", elementName);
                                break;
                            }
                        }
                    }
                    // build containment and add it to parent siblings
                    builder.addSibling(containmentBuilder.build());
                    LOG.debug("Adding new ContainmentNode under {} node.", elementName);
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    final var text = reader.getText();
                    // check if there are non whitespace characters in text
                    if (!text.trim().isEmpty()) {
                        // create content match if there are some data inside
                        LOG.debug("Parsing content of {} node.", elementName);
                        final var nameValueMap = new HashMap<QName, Object>();
                        for (final var entry : children) {
                            final var node = context.findDataTreeChild(entry.toSchemaNodeIdentifier()
                                .getNodeIdentifiers()).orElseThrow(() ->
                                    new XMLStreamException("Failed to lookup node %s in schema context".formatted(entry
                                        .currentStatement())));
                            if (node instanceof TypedDataSchemaNode typed) {
                                try {
                                    // unescape characters before parsing them
                                    final var unescaped = StringEscapeUtils.unescapeXml(text);
                                    final var value = codec.codecFor(typed, entry).parseValue(nsContext, unescaped);
                                    nameValueMap.put(node.getQName(), value);
                                    LOG.debug("Successfully parsed value for {} node.", node.getQName());
                                } catch (IllegalArgumentException e) {
                                    // ignore values that codec failed to parse
                                    LOG.debug("Incompatible value for node {}. Ignoring.", node.getQName());
                                }
                            }
                        }
                        if (nameValueMap.isEmpty()) {
                            throw new XMLStreamException("Failed to lookup any node with compatible type for node %s."
                                .formatted(elementLocalName));
                        }
                        builder.addSibling(new ContentMatchNode(namespace, nameValueMap));
                        LOG.debug("Adding new ContentMatch under {} node.", elementName);
                        return;
                    }
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    // end element right after start means this is empty element - create selection
                    LOG.debug("Parsing SelectionNode under {} node.", elementName);
                    final var selection = SelectionNode.builder(namespace);
                    // add attributes if there are some
                    if (!attributes.isEmpty()) {
                        attributes.forEach(selection::add);
                    }
                    builder.addSibling(selection.build());
                    LOG.debug("Adding new SelectionNode under {} node.", elementName);
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
     * Find element by its local name {@code elementLocalName} in a module {@code module}.
     *
     * <p>We are searching for presence of a node in some parent stack and selected module, when we find it
     * we add its QName to collection of QNames and currents stack position to collection of children stacks
     * which will be used as parents when searching for children of this node.
     *
     * @param elementLocalName the local name of element we are looking for
     * @param module module we are processing
     * @param parentStack all possible parent of searching node
     * @param children collection of stacks
     * @param childrenQNames collection of QNames
     */
    private static void findNodeInModule(final String elementLocalName, final ModuleEffectiveStatement module,
            final SchemaInferenceStack parentStack, final ArrayList<SchemaInferenceStack> children,
            final ArrayList<QName> childrenQNames) {
        final var qname = QName.create(module.localQNameModule(), elementLocalName);
        LOG.debug("Trying to find node {} under {}.", qname, module.localQNameModule());
        try {
            if (!parentStack.isEmpty() && parentStack.currentStatement() instanceof ChoiceSchemaNode choice) {
                LOG.debug("Searching through choice node {} for match {}.", choice.getQName(), qname);
                for (final var caseNode : choice.getCases()) {
                    final var child = caseNode.findDataTreeChild(qname);
                    if (child.isPresent()) {
                        parentStack.enterSchemaTree(caseNode.getQName());
                        final var exact = parentStack.enterSchemaTree(qname);
                        childrenQNames.add(exact.argument());
                        children.add(parentStack.copy());
                        parentStack.exit();
                        parentStack.exit();
                        break;
                    }
                }
            } else {
                final var exact = parentStack.enterSchemaTree(qname);
                childrenQNames.add(exact.argument());
                children.add(parentStack.copy());
                parentStack.exit();
            }
        } catch (IllegalArgumentException iae) {
            LOG.debug("Failed to find node with name {}.", qname);
        }
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
        LOG.debug("Parsing attributes for {} node.", reader.getName());
        final var childAttributes = new ArrayList<AttributeMatch>();
        final var attrCount = reader.getAttributeCount();
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
                exactNamespace = new Exact(QName.create(it.next().localQNameModule(), attrName.getLocalPart()));
            } else {
                throw new XMLStreamException("Failed to lookup module schema for namespace " + attrNamespace);
            }
            // FIXME YANGTOOLS-1444 parse text and create object value from attribute text when we can parse propagate
            //  YANG-modeled annotations
            final var attrValue = reader.getAttributeValue(i);
            childAttributes.add(new AttributeMatch(exactNamespace, attrValue));
        }
        LOG.debug("Found {} attributes for {} node.", childAttributes.size(), reader.getName());
        return childAttributes;
    }
}
