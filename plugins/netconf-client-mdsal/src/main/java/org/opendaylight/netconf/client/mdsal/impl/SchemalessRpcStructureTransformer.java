/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_OPERATION_QNAME;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.NETCONF_DATA_QNAME;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.appendListKeyNodes;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.writeSchemalessFilter;

import java.util.List;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Transforms rpc structures to anyxml and vice versa. Provides support for devices, which don't expose their schema.
 */
class SchemalessRpcStructureTransformer implements RpcStructureTransformer {

    /**
     * Selects elements in anyxml data node, which matches path arguments QNames. Since class in not context aware,
     * method searches for all elements as they are named in path.
     * @param data data, must be of type {@link DOMSourceAnyxmlNode}
     * @param path path to select
     * @return selected data
     */
    @Override
    public Optional<NormalizedNode> selectFromDataStructure(final DataContainerChild data,
            final YangInstanceIdentifier path) {
        if (!(data instanceof DOMSourceAnyxmlNode anyxml)) {
            throw new IllegalArgumentException("Unexpected data " + data.prettyTree());
        }

        final var result = XmlUtil.newDocument();
        final var dataElement = result.createElementNS(NETCONF_DATA_QNAME.getNamespace().toString(),
            NETCONF_DATA_QNAME.getLocalName());
        result.appendChild(dataElement);
        for (var xmlElement : selectMatchingNodes(getSourceElement(anyxml.body()), path)) {
            dataElement.appendChild(result.importNode(xmlElement.getDomElement(), true));
        }
        return Optional.of(Builders.anyXmlBuilder()
            .withNodeIdentifier(NETCONF_DATA_NODEID)
            .withValue(new DOMSource(result))
            .build());
    }

    /**
     * This class in not context aware. All elements are present in resulting structure, which are present in data path.
     * @see RpcStructureTransformer#createEditConfigStructure(Optional, YangInstanceIdentifier, Optional)
     * @param data data
     * @param dataPath path, where data will be written
     * @param operation operation
     * @return config structure
     */
    @Override
    public DOMSourceAnyxmlNode createEditConfigStructure(final Optional<NormalizedNode> data,
            final YangInstanceIdentifier dataPath, final Optional<EffectiveOperation> operation) {
        final var dataValue = data.orElseThrow();
        if (!(dataValue instanceof DOMSourceAnyxmlNode anxmlData)) {
            throw new IllegalArgumentException("Unexpected data " + dataValue.prettyTree());
        }

        final var document = XmlUtil.newDocument();
        final var dataNode = (Element) document.importNode(getSourceElement(anxmlData.body()), true);
        checkDataValidForPath(dataPath, dataNode);

        final var configElement = document.createElementNS(NETCONF_CONFIG_QNAME.getNamespace().toString(),
                NETCONF_CONFIG_QNAME.getLocalName());
        document.appendChild(configElement);

        final Element parentXmlStructure;
        if (dataPath.isEmpty()) {
            parentXmlStructure = dataNode;
            configElement.appendChild(parentXmlStructure);
        } else {
            final var pathArguments = dataPath.getPathArguments();
            // last will be appended later
            parentXmlStructure = instanceIdToXmlStructure(pathArguments.subList(0, pathArguments.size() - 1),
                configElement);
        }
        operation.ifPresent(modifyAction -> setOperationAttribute(modifyAction, document, dataNode));
        //append data
        parentXmlStructure.appendChild(document.importNode(dataNode, true));
        return Builders.anyXmlBuilder()
            .withNodeIdentifier(NETCONF_CONFIG_NODEID)
            .withValue(new DOMSource(document.getDocumentElement()))
            .build();
    }

    /**
     * This class in not context aware. All elements are present in resulting structure, which are present in data path.
     * @see RpcStructureTransformer#toFilterStructure(YangInstanceIdentifier)
     * @param path path
     * @return filter structure
     */
    @Override
    public AnyxmlNode<?> toFilterStructure(final YangInstanceIdentifier path) {
        final var document = XmlUtil.newDocument();
        instanceIdToXmlStructure(path.getPathArguments(), prepareFilterElement(document));
        return buildFilterXmlNode(document);
    }

    @Override
    public AnyxmlNode<?> toFilterStructure(final List<FieldsFilter> fieldsFilters) {
        final var document = XmlUtil.newDocument();
        final var filterElement = prepareFilterElement(document);
        for (var filter : fieldsFilters) {
            writeSchemalessFilter(filter.path(), filter.fields(), filterElement);
        }
        return buildFilterXmlNode(document);
    }

    private static Element prepareFilterElement(final Document document) {
        // FIXME: use a constant
        final var filterNs = NETCONF_FILTER_QNAME.getNamespace().toString();
        final var filter = document.createElementNS(filterNs, NETCONF_FILTER_QNAME.getLocalName());
        final var attr = document.createAttributeNS(filterNs, "type");
        attr.setTextContent("subtree");
        filter.setAttributeNode(attr);
        document.appendChild(filter);
        return filter;
    }

    private static AnyxmlNode<?> buildFilterXmlNode(final Document document) {
        return Builders.anyXmlBuilder()
            .withNodeIdentifier(NETCONF_FILTER_NODEID)
            .withValue(new DOMSource(document.getDocumentElement()))
            .build();
    }

    private static void checkDataValidForPath(final YangInstanceIdentifier dataPath, final Element dataNode) {
        //if datapath is empty, consider dataNode to be a root node
        if (dataPath.isEmpty()) {
            return;
        }
        final var dataElement = XmlElement.fromDomElement(dataNode);
        final var  lastPathArgument = dataPath.getLastPathArgument();
        final var nodeType = lastPathArgument.getNodeType();
        if (!nodeType.getNamespace().toString().equals(dataNode.getNamespaceURI())
                || !nodeType.getLocalName().equals(dataElement.getName())) {
            throw new IllegalStateException(
                    String.format("Can't write data '%s' to path %s", dataNode.getTagName(), dataPath));
        }
        if (lastPathArgument instanceof NodeIdentifierWithPredicates) {
            checkKeyValuesValidForPath(dataElement, lastPathArgument);
        }
    }

    private static void checkKeyValuesValidForPath(final XmlElement dataElement, final PathArgument lastPathArgument) {
        for (var entry : ((NodeIdentifierWithPredicates) lastPathArgument).entrySet()) {
            final var qname = entry.getKey();
            final var key = dataElement.getChildElementsWithinNamespace(qname.getLocalName(),
                qname.getNamespace().toString());
            if (key.isEmpty()) {
                throw new IllegalStateException("No key present in xml");
            }
            if (key.size() > 1) {
                throw new IllegalStateException("Multiple values for same key present");
            }
            final String textContent;
            try {
                textContent = key.get(0).getTextContent();
            } catch (DocumentedException e) {
                throw new IllegalStateException("Key value not present in key element", e);
            }
            if (!entry.getValue().equals(textContent)) {
                throw new IllegalStateException("Key value in path not equal to key value in xml");
            }
        }
    }

    private static void setOperationAttribute(final EffectiveOperation operation, final Document document,
            final Element dataNode) {
        final var operationAttribute = document.createAttributeNS(NETCONF_OPERATION_QNAME.getNamespace().toString(),
            NETCONF_OPERATION_QNAME.getLocalName());
        operationAttribute.setTextContent(operation.xmlValue());
        dataNode.setAttributeNode(operationAttribute);
    }

    private static Element instanceIdToXmlStructure(final List<PathArgument> pathArguments, final Element data) {
        final var doc = data.getOwnerDocument();
        var parent = data;
        for (var pathArgument : pathArguments) {
            final var nodeType = pathArgument.getNodeType();
            final var element = doc.createElementNS(nodeType.getNamespace().toString(), nodeType.getLocalName());
            parent.appendChild(element);
            //if path argument is list id, add also keys to resulting xml
            if (pathArgument instanceof NodeIdentifierWithPredicates nip) {
                appendListKeyNodes(element, nip);
            }
            parent = element;
        }
        return parent;
    }

    private static List<XmlElement> selectMatchingNodes(final Element domElement, final YangInstanceIdentifier path) {
        var element = XmlElement.fromDomElement(domElement);
        for (var pathArgument : path.getPathArguments()) {
            var childElements = element.getChildElements(pathArgument.getNodeType().getLocalName());
            if (childElements.size() == 1) {
                element = childElements.get(0);
            } else {
                return childElements;
            }
        }
        return List.of(element);
    }

    private static Element getSourceElement(final DOMSource source) {
        final var node = source.getNode();
        return switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE -> ((Document) node).getDocumentElement();
            case Node.ELEMENT_NODE -> (Element) node;
            default -> throw new IllegalStateException("DOMSource node must be document or element.");
        };
    }
}
