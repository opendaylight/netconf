/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_FILTER_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_OPERATION_QNAME;
import static org.opendaylight.netconf.util.NetconfUtil.NETCONF_DATA_QNAME;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.w3c.dom.Attr;
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
     * @param data data, must be of type {@link AnyXmlNode}
     * @param path path to select
     * @return selected data
     */
    @Override
    public Optional<NormalizedNode<?, ?>> selectFromDataStructure(
            final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> data,
            final YangInstanceIdentifier path) {
        Preconditions.checkArgument(data instanceof DOMSourceAnyxmlNode);
        final List<XmlElement> xmlElements = selectMatchingNodes(
            getSourceElement(((DOMSourceAnyxmlNode)data).getValue()), path);
        final Document result = XmlUtil.newDocument();
        final Element dataElement =
                result.createElementNS(NETCONF_DATA_QNAME.getNamespace().toString(), NETCONF_DATA_QNAME.getLocalName());
        result.appendChild(dataElement);
        for (XmlElement xmlElement : xmlElements) {
            dataElement.appendChild(result.importNode(xmlElement.getDomElement(), true));
        }
        final DOMSourceAnyxmlNode resultAnyxml = Builders.anyXmlBuilder()
                .withNodeIdentifier(NETCONF_DATA_NODEID)
                .withValue(new DOMSource(result))
                .build();
        return Optional.of(resultAnyxml);
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
    public DOMSourceAnyxmlNode createEditConfigStructure(final Optional<NormalizedNode<?, ?>> data,
                                                final YangInstanceIdentifier dataPath,
                                                final Optional<ModifyAction> operation) {
        Preconditions.checkArgument(data.isPresent());
        Preconditions.checkArgument(data.get() instanceof DOMSourceAnyxmlNode);

        final DOMSourceAnyxmlNode anxmlData = (DOMSourceAnyxmlNode) data.get();
        final Document document = XmlUtil.newDocument();
        final Element dataNode = (Element) document.importNode(getSourceElement(anxmlData.getValue()), true);
        checkDataValidForPath(dataPath, dataNode);

        final Element configElement = document.createElementNS(NETCONF_CONFIG_QNAME.getNamespace().toString(),
                NETCONF_CONFIG_QNAME.getLocalName());
        document.appendChild(configElement);

        final Element parentXmlStructure;
        if (dataPath.isEmpty()) {
            parentXmlStructure = dataNode;
            configElement.appendChild(parentXmlStructure);
        } else {
            final List<YangInstanceIdentifier.PathArgument> pathArguments = dataPath.getPathArguments();
            //last will be appended later
            final List<YangInstanceIdentifier.PathArgument> pathWithoutLast =
                    pathArguments.subList(0, pathArguments.size() - 1);
            parentXmlStructure = instanceIdToXmlStructure(pathWithoutLast, configElement);
        }
        if (operation.isPresent()) {
            setOperationAttribute(operation, document, dataNode);
        }
        //append data
        parentXmlStructure.appendChild(document.importNode(dataNode, true));
        return Builders.anyXmlBuilder().withNodeIdentifier(NETCONF_CONFIG_NODEID)
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
    public DataContainerChild<?, ?> toFilterStructure(final YangInstanceIdentifier path) {
        final Document document = XmlUtil.newDocument();
        final String filterNs = NETCONF_FILTER_QNAME.getNamespace().toString();
        final Element filter = document.createElementNS(filterNs, NETCONF_FILTER_QNAME.getLocalName());
        final Attr a = document.createAttributeNS(filterNs, "type");
        a.setTextContent("subtree");
        filter.setAttributeNode(a);
        document.appendChild(filter);
        instanceIdToXmlStructure(path.getPathArguments(), filter);
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
        final XmlElement dataElement = XmlElement.fromDomElement(dataNode);
        final YangInstanceIdentifier.PathArgument lastPathArgument = dataPath.getLastPathArgument();
        final QName nodeType = lastPathArgument.getNodeType();
        if (!nodeType.getNamespace().toString().equals(dataNode.getNamespaceURI())
                || !nodeType.getLocalName().equals(dataElement.getName())) {
            throw new IllegalStateException(
                    String.format("Can't write data '%s' to path %s", dataNode.getTagName(), dataPath));
        }
        if (lastPathArgument instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
            checkKeyValuesValidForPath(dataElement, lastPathArgument);
        }

    }

    private static void checkKeyValuesValidForPath(final XmlElement dataElement,
                                            final YangInstanceIdentifier.PathArgument lastPathArgument) {
        final YangInstanceIdentifier.NodeIdentifierWithPredicates keyedId =
                (YangInstanceIdentifier.NodeIdentifierWithPredicates) lastPathArgument;
        for (Entry<QName, Object> entry : keyedId.entrySet()) {
            QName qualifiedName = entry.getKey();
            final List<XmlElement> key =
                    dataElement.getChildElementsWithinNamespace(qualifiedName.getLocalName(),
                            qualifiedName.getNamespace().toString());
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

    private static void setOperationAttribute(final Optional<ModifyAction> operation, final Document document,
                                       final Element dataNode) {
        final Attr operationAttribute = document.createAttributeNS(NETCONF_OPERATION_QNAME.getNamespace().toString(),
            NETCONF_OPERATION_QNAME.getLocalName());
        operationAttribute.setTextContent(toOperationString(operation.get()));
        dataNode.setAttributeNode(operationAttribute);
    }

    private static Element instanceIdToXmlStructure(final List<YangInstanceIdentifier.PathArgument> pathArguments,
                                                    final Element data) {
        final Document doc = data.getOwnerDocument();
        Element parent = data;
        for (YangInstanceIdentifier.PathArgument pathArgument : pathArguments) {
            final QName nodeType = pathArgument.getNodeType();
            final Element element = doc.createElementNS(nodeType.getNamespace().toString(), nodeType.getLocalName());
            parent.appendChild(element);
            //if path argument is list id, add also keys to resulting xml
            if (pathArgument instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
                YangInstanceIdentifier.NodeIdentifierWithPredicates listNode =
                        (YangInstanceIdentifier.NodeIdentifierWithPredicates) pathArgument;
                for (Entry<QName, Object> key : listNode.entrySet()) {
                    final Element keyElement =
                            doc.createElementNS(key.getKey().getNamespace().toString(), key.getKey().getLocalName());
                    keyElement.setTextContent(key.getValue().toString());
                    element.appendChild(keyElement);
                }
            }
            parent = element;
        }
        return parent;
    }

    private static List<XmlElement> selectMatchingNodes(final Element domElement, final YangInstanceIdentifier path) {
        XmlElement element = XmlElement.fromDomElement(domElement);
        for (YangInstanceIdentifier.PathArgument pathArgument : path.getPathArguments()) {
            List<XmlElement> childElements = element.getChildElements(pathArgument.getNodeType().getLocalName());
            if (childElements.size() == 1) {
                element = childElements.get(0);
            } else {
                return childElements;
            }
        }
        return Collections.singletonList(element);
    }

    private static String toOperationString(final ModifyAction operation) {
        return operation.name().toLowerCase(Locale.ROOT);
    }

    private static Element getSourceElement(final DOMSource source) {
        final Node node = source.getNode();
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE:
                return ((Document)node).getDocumentElement();
            case Node.ELEMENT_NODE:
                return (Element) node;
            default:
                throw new IllegalStateException("DOMSource node must be document or element.");
        }
    }

}
