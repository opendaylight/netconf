/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import static org.opendaylight.yangtools.yang.data.util.ParserStreamUtils.findSchemaNodeByNameAndNamespace;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.MissingNameSpaceException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * Class validates filter content against schema context.
 */
public class FilterContentValidator {

    private final CurrentSchemaContext schemaContext;

    /**
     * @param schemaContext current schema context
     */
    public FilterContentValidator(final CurrentSchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    /**
     * Validates filter content against this validator schema context. If the filter is valid,
     * method returns {@link YangInstanceIdentifier} of node which can be used as root for data selection.
     * @param filterContent filter content
     * @return YangInstanceIdentifier
     * @throws DocumentedException if filter content is not valid
     */
    public YangInstanceIdentifier validate(final XmlElement filterContent) throws DocumentedException {
        try {
            final URI namespace = new URI(filterContent.getNamespace());
            final Module module = schemaContext.getCurrentContext().findModuleByNamespaceAndRevision(namespace, null);
            final DataSchemaNode schema = getRootDataSchemaNode(module, namespace, filterContent.getName());
            final FilterTree filterTree = validateNode(filterContent, schema, new FilterTree(schema.getQName(),
                    Type.OTHER, schema));
            return getFilterDataRoot(filterTree, filterContent, YangInstanceIdentifier.builder());
        } catch (final DocumentedException e) {
            throw e;
        } catch (final Exception e) {
            throw new DocumentedException("Validation failed. Cause: " + e.getMessage(),
                    DocumentedException.ErrorType.APPLICATION,
                    DocumentedException.ErrorTag.UNKNOWN_NAMESPACE,
                    DocumentedException.ErrorSeverity.ERROR);
        }
    }

    /**
     * Returns module's child data node of given name space and name
     * @param module module
     * @param nameSpace name space
     * @param name name
     * @return child data node schema
     * @throws DocumentedException if child with given name is not present
     */
    private DataSchemaNode getRootDataSchemaNode(final Module module, final URI nameSpace, final String name)
            throws DocumentedException {
        final Collection<DataSchemaNode> childNodes = module.getChildNodes();
        for (final DataSchemaNode childNode : childNodes) {
            final QName qName = childNode.getQName();
            if (qName.getNamespace().equals(nameSpace) && qName.getLocalName().equals(name)) {
                return childNode;
            }
        }
        throw new DocumentedException("Unable to find node with namespace: " + nameSpace + "in schema context: " +
                schemaContext.getCurrentContext().toString(),
                DocumentedException.ErrorType.APPLICATION,
                DocumentedException.ErrorTag.UNKNOWN_NAMESPACE,
                DocumentedException.ErrorSeverity.ERROR);
    }

    /**
     * Recursively checks filter elements against the schema. Returns tree of nodes QNames as they appear in filter.
     * @param element element to check
     * @param parentNodeSchema parent node schema
     * @param tree parent node tree
     * @return tree
     * @throws ValidationException if filter content is not valid
     */
    private static FilterTree validateNode(final XmlElement element, final DataSchemaNode parentNodeSchema,
                                           final FilterTree tree) throws ValidationException {
        final List<XmlElement> childElements = element.getChildElements();
        for (final XmlElement childElement : childElements) {
            try {
                final Deque<DataSchemaNode> path = findSchemaNodeByNameAndNamespace(parentNodeSchema,
                        childElement.getName(), new URI(childElement.getNamespace()));
                if (path.isEmpty()) {
                    throw new ValidationException(element, childElement);
                }
                FilterTree subtree = tree;
                for (final DataSchemaNode dataSchemaNode : path) {
                    subtree = subtree.addChild(dataSchemaNode);
                }
                final DataSchemaNode childSchema = path.getLast();
                validateNode(childElement, childSchema, subtree);
            } catch (URISyntaxException | MissingNameSpaceException e) {
                throw new RuntimeException("Wrong namespace in element + " + childElement.toString());
            }
        }
        return tree;
    }

    /**
     * Searches for YangInstanceIdentifier of node, which can be used as root for data selection.
     * It goes as deep in tree as possible. Method stops traversing, when there are multiple child elements. If element
     * represents list and child elements are key values, then it builds YangInstanceIdentifier of list entry.
     * @param tree QName tree
     * @param filterContent filter element
     * @param builder builder  @return YangInstanceIdentifier
     */
    private static YangInstanceIdentifier getFilterDataRoot(FilterTree tree, final XmlElement filterContent,
                                                            final InstanceIdentifierBuilder builder) {
        builder.node(tree.getName());
        final List<String> path = new ArrayList<>();
        while (tree.getChildren().size() == 1) {
            final FilterTree child = tree.getChildren().iterator().next();
            if (child.getType() == Type.CHOICE_CASE) {
                tree = child;
                continue;
            }
            builder.node(child.getName());
            path.add(child.getName().getLocalName());
            if (child.getType() == Type.LIST) {
                appendKeyIfPresent(child, filterContent, path, builder);
                return builder.build();
            }
            tree = child;
        }
        return builder.build();
    }

    private static void appendKeyIfPresent(final FilterTree tree, final XmlElement filterContent,
                                           final List<String> pathToList,
                                           final InstanceIdentifierBuilder builder) {
        Preconditions.checkArgument(tree.getSchemaNode() instanceof ListSchemaNode);
        final ListSchemaNode listSchemaNode = (ListSchemaNode) tree.getSchemaNode();
        final List<QName> keyDefinition = listSchemaNode.getKeyDefinition();
        final Map<QName, Object> map = getKeyValues(pathToList, filterContent, keyDefinition);
        if (map != null) {
            builder.nodeWithKey(tree.getName(), map);
        }
    }

    private static Map<QName, Object> getKeyValues(final List<String> path, final XmlElement filterContent,
                                                   final List<QName> keyDefinition) {
        XmlElement current = filterContent;
        //
        for (final String pathElement : path) {
            final List<XmlElement> childElements = filterContent.getChildElements(pathElement);
            if (childElements.size() != 1) {
                return null;
            }
            current = childElements.get(0);
        }
        final Map<QName, Object> keys = new HashMap<>();
        for (final QName qName : keyDefinition) {
            final Optional<XmlElement> childElements = current.getOnlyChildElementOptionally(qName.getLocalName());
            if (!childElements.isPresent()) {
                return null;
            }
            final Optional<String> keyValue = childElements.get().getOnlyTextContentOptionally();
            if (keyValue.isPresent()) {
                keys.put(qName, keyValue.get());
            }
        }
        return keys;
    }

    /**
     * Class represents tree of QNames as they are present in the filter.
     */
    private static class FilterTree {

        private final QName name;
        private final Type type;
        private final DataSchemaNode schemaNode;
        private final Map<QName, FilterTree> children;

        FilterTree(final QName name, final Type type, final DataSchemaNode schemaNode) {
            this.name = name;
            this.type = type;
            this.schemaNode = schemaNode;
            this.children = new HashMap<>();
        }

        FilterTree addChild(final DataSchemaNode data) {
            final Type type;
            if (data instanceof ChoiceCaseNode) {
                type = Type.CHOICE_CASE;
            } else if (data instanceof ListSchemaNode) {
                type = Type.LIST;
            } else {
                type = Type.OTHER;
            }
            final QName name = data.getQName();
            FilterTree childTree = children.get(name);
            if (childTree == null) {
                childTree = new FilterTree(name, type, data);
            }
            children.put(name, childTree);
            return childTree;
        }

        Collection<FilterTree> getChildren() {
            return children.values();
        }

        QName getName() {
            return name;
        }

        Type getType() {
            return type;
        }

        DataSchemaNode getSchemaNode() {
            return schemaNode;
        }
    }

    private enum Type {
        LIST, CHOICE_CASE, OTHER
    }

    private static class ValidationException extends Exception {
        public ValidationException(final XmlElement parent, final XmlElement child) {
            super("Element " + child + " can't be child of " + parent);
        }
    }

}
