/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.opendaylight.yangtools.yang.data.util.ParserStreamUtils.findSchemaNodeByNameAndNamespace;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.MissingNameSpaceException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.data.util.codec.TypeAwareCodec;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * Class validates filter content against schema context.
 */
public class FilterContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(FilterContentValidator.class);
    private final CurrentSchemaContext schemaContext;

    public FilterContentValidator(final CurrentSchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    /**
     * Validates filter content against this validator schema context. If the filter is valid,
     * method returns {@link YangInstanceIdentifier} of node which can be used as root for data selection.
     *
     * @param filterContent filter content
     * @return YangInstanceIdentifier
     * @throws DocumentedException if filter content validation failed
     */
    public YangInstanceIdentifier validate(final XmlElement filterContent) throws DocumentedException {
        final XMLNamespace namespace;
        try {
            namespace = XMLNamespace.of(filterContent.getNamespace());
        } catch (final IllegalArgumentException e) {
            // FIXME: throw DocumentedException
            throw new IllegalArgumentException("Wrong namespace in element + " + filterContent.toString(), e);
        }

        try {
            final Module module = schemaContext.getCurrentContext().findModules(namespace).iterator().next();
            final DataSchemaNode schema = getRootDataSchemaNode(module, namespace, filterContent.getName());
            final FilterTree filterTree = validateNode(
                    filterContent, schema, new FilterTree(schema.getQName(), Type.OTHER, schema));
            return getFilterDataRoot(filterTree, filterContent, YangInstanceIdentifier.builder());
        } catch (final ValidationException e) {
            LOG.debug("Filter content isn't valid", e);
            throw new DocumentedException("Validation failed. Cause: " + e.getMessage(), e,
                    ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }
    }

    /**
     * Returns module's child data node of given name space and name.
     *
     * @param module    module
     * @param nameSpace name space
     * @param name      name
     * @return child data node schema
     * @throws DocumentedException if child with given name is not present
     */
    private DataSchemaNode getRootDataSchemaNode(final Module module, final XMLNamespace nameSpace, final String name)
            throws DocumentedException {
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            final QName qName = childNode.getQName();
            if (qName.getNamespace().equals(nameSpace) && qName.getLocalName().equals(name)) {
                return childNode;
            }
        }
        throw new DocumentedException("Unable to find node with namespace: " + nameSpace + " in schema context: "
                + schemaContext.getCurrentContext(),
                ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
    }

    /**
     * Recursively checks filter elements against the schema. Returns tree of nodes QNames as they appear in filter.
     *
     * @param element          element to check
     * @param parentNodeSchema parent node schema
     * @param tree             parent node tree
     * @return tree
     * @throws ValidationException if filter content is not valid
     */
    private FilterTree validateNode(final XmlElement element, final DataSchemaNode parentNodeSchema,
                                    final FilterTree tree) throws ValidationException {
        for (final XmlElement childElement : element.getChildElements()) {
            try {
                final Deque<DataSchemaNode> path = findSchemaNodeByNameAndNamespace(parentNodeSchema,
                        childElement.getName(), XMLNamespace.of(childElement.getNamespace()));
                if (path.isEmpty()) {
                    throw new ValidationException(element, childElement);
                }
                FilterTree subtree = tree;
                for (final DataSchemaNode dataSchemaNode : path) {
                    subtree = subtree.addChild(dataSchemaNode);
                }
                final DataSchemaNode childSchema = path.getLast();
                validateNode(childElement, childSchema, subtree);
            } catch (IllegalArgumentException | MissingNameSpaceException e) {
                throw new IllegalArgumentException("Wrong namespace in element + " + childElement.toString(), e);
            }
        }
        return tree;
    }

    /**
     * Searches for YangInstanceIdentifier of node, which can be used as root for data selection.
     * It goes as deep in tree as possible. Method stops traversing, when there are multiple child elements. If element
     * represents list and child elements are key values, then it builds YangInstanceIdentifier of list entry.
     *
     * @param tree          QName tree
     * @param filterContent filter element
     * @param builder       builder  @return YangInstanceIdentifier
     */
    private YangInstanceIdentifier getFilterDataRoot(final FilterTree tree, final XmlElement filterContent,
                                                     final InstanceIdentifierBuilder builder) {
        builder.node(tree.getName());
        final List<String> path = new ArrayList<>();

        FilterTree current = tree;
        while (current.size() == 1) {
            final FilterTree child = current.getChildren().iterator().next();
            if (child.getType() == Type.CHOICE_CASE) {
                current = child;
                continue;
            }
            builder.node(child.getName());
            path.add(child.getName().getLocalName());
            if (child.getType() == Type.LIST) {
                appendKeyIfPresent(current, child, filterContent, path, builder);
                return builder.build();
            }
            current = child;
        }
        return builder.build();
    }

    private void appendKeyIfPresent(final FilterTree parent, final FilterTree list, final XmlElement filterContent,
            final List<String> pathToList, final InstanceIdentifierBuilder builder) {
        Preconditions.checkArgument(list.getSchemaNode() instanceof ListSchemaNode);
        final ListSchemaNode listSchemaNode = (ListSchemaNode) list.getSchemaNode();
        final DataSchemaNode parentSchemaNode = parent.getSchemaNode();

        final Map<QName, Object> map = getKeyValues(pathToList, filterContent, parentSchemaNode, listSchemaNode);
        if (!map.isEmpty()) {
            builder.nodeWithKey(list.getName(), map);
        }
    }

    private Map<QName, Object> getKeyValues(final List<String> path, final XmlElement filterContent,
            final DataSchemaNode parentSchemaNode, final ListSchemaNode listSchemaNode) {
        XmlElement current = filterContent;
        //find list element
        for (final String pathElement : path) {
            final List<XmlElement> childElements = current.getChildElements(pathElement);
            // if there are multiple list entries present in the filter, we can't use any keys and must read whole list
            if (childElements.size() != 1) {
                return Map.of();
            }
            current = childElements.get(0);
        }
        final Map<QName, Object> keys = new HashMap<>();
        final List<QName> keyDefinition = listSchemaNode.getKeyDefinition();
        for (final QName qualifiedName : keyDefinition) {
            final var optChildElements = current.getOnlyChildElementOptionally(qualifiedName.getLocalName());
            if (optChildElements.isEmpty()) {
                return Map.of();
            }
            optChildElements.orElseThrow().getOnlyTextContentOptionally().ifPresent(keyValue -> {
                final LeafSchemaNode listKey = (LeafSchemaNode) listSchemaNode.getDataChildByName(qualifiedName);
                if (listKey instanceof IdentityrefTypeDefinition) {
                    keys.put(qualifiedName, keyValue);
                } else {
                    final TypeDefinition<? extends TypeDefinition<?>> keyType = listKey.getType();
                    if (keyType instanceof IdentityrefTypeDefinition || keyType instanceof LeafrefTypeDefinition) {
                        final Document document = filterContent.getDomElement().getOwnerDocument();
                        final NamespaceContext nsContext = new UniversalNamespaceContextImpl(document, false);
                        final EffectiveModelContext modelContext = schemaContext.getCurrentContext();
                        final XmlCodecFactory xmlCodecFactory = XmlCodecFactory.create(modelContext);
                        final SchemaInferenceStack resolver = SchemaInferenceStack.of(modelContext, Absolute.of(
                                parentSchemaNode.getQName(), listSchemaNode.getQName(), listKey.getQName()));
                        final TypeAwareCodec<?, NamespaceContext, XMLStreamWriter> typeCodec = xmlCodecFactory
                                .codecFor(listKey, resolver);
                        final Object deserializedKeyValue = typeCodec.parseValue(nsContext, keyValue);
                        keys.put(qualifiedName, deserializedKeyValue);
                    } else {
                        final Object deserializedKey = TypeDefinitionAwareCodec.from(keyType).deserialize(keyValue);
                        keys.put(qualifiedName, deserializedKey);
                    }
                }
            });
        }
        return keys;
    }

    private enum Type {
        LIST, CHOICE_CASE, OTHER
    }

    /**
     * Class represents tree of QNames as they are present in the filter.
     */
    private static final class FilterTree {
        private final Map<QName, FilterTree> children = new HashMap<>();
        private final DataSchemaNode schemaNode;
        private final QName name;
        private final Type type;

        FilterTree(final QName name, final Type type, final DataSchemaNode schemaNode) {
            this.name = name;
            this.type = type;
            this.schemaNode = schemaNode;
        }

        FilterTree addChild(final DataSchemaNode data) {
            final Type childType;
            if (data instanceof CaseSchemaNode) {
                childType = Type.CHOICE_CASE;
            } else if (data instanceof ListSchemaNode) {
                childType = Type.LIST;
            } else {
                childType = Type.OTHER;
            }
            final QName childName = data.getQName();
            FilterTree childTree = children.get(childName);
            if (childTree == null) {
                childTree = new FilterTree(childName, childType, data);
            }
            children.put(childName, childTree);
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

        int size() {
            return children.size();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("name", name).add("type", type).add("size", size()).toString();
        }
    }

    private static class ValidationException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        ValidationException(final XmlElement parent, final XmlElement child) {
            super("Element " + child + " can't be child of " + parent);
        }
    }
}
