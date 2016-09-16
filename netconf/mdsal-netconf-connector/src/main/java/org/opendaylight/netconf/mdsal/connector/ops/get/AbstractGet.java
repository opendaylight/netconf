/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AbstractGet extends AbstractSingletonNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGet.class);

    protected static final String FILTER = "filter";
    static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.builder().build();
    protected final CurrentSchemaContext schemaContext;
    private final FilterContentValidator validator;

    public AbstractGet(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.validator = new FilterContentValidator(schemaContext);
    }

    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    protected Node transformNormalizedNode(final Document document, final NormalizedNode<?, ?> data, final YangInstanceIdentifier dataRoot) {

        final DOMResult result = new DOMResult(document.createElement(XmlNetconfConstants.DATA_KEY));

        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);

        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext.getCurrentContext(), getSchemaPath(dataRoot));

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true);

        writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
        return result.getNode();
    }

    private XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Function<PathArgument, QName> PATH_ARG_TO_QNAME = new Function<YangInstanceIdentifier.PathArgument, QName>() {
        @Override
        public QName apply(final YangInstanceIdentifier.PathArgument input) {
            return input.getNodeType();
        }
    };

    private SchemaPath getSchemaPath(final YangInstanceIdentifier dataRoot) {
        return SchemaPath.create(Iterables.transform(dataRoot.getPathArguments(), PATH_ARG_TO_QNAME), dataRoot.equals(ROOT));
    }

    private void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter, final ContainerNode data) {
        try {
            if (data.getNodeType().equals(SchemaContext.NAME)) {
                for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
                    nnWriter.write(child);
                }
            } else {
                nnWriter.write(data);
            }
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            Throwables.propagate(e);
        }
    }


    protected Element mergeDataNodesToOneElement(final List<Element> nodes) {
        final Node mainNode = nodes.get(0);
        for (Element node : nodes) {
            final NodeList childNodes = node.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                mainNode.insertBefore(childNodes.item(j), null);
            }
        }
        return (Element) mainNode;
    }

    protected Element serializeNodeWithParentStructure(Document document, YangInstanceIdentifier dataRoot, NormalizedNode node) {
        if (!dataRoot.equals(ROOT)) {
            return (Element) transformNormalizedNode(document,
                    ImmutableNodes.fromInstanceId(schemaContext.getCurrentContext(), dataRoot, node), ROOT);
        }
        return  (Element) transformNormalizedNode(document, node, ROOT);
    }

    /**
     *
     * @param operationElement operation element
     * @return if Filter is present and not empty returns List of the InstanceIdentifiers to the read locations in datastore.
     *          empty filter returns empty list which should equal an empty &lt;data/&gt; container in the response.
     *         if filter is not present we want to read the entire datastore - return list of single element ROOT.
     * @throws DocumentedException
     */
    protected List<YangInstanceIdentifier> getDataRootsFromFilter(XmlElement operationElement) throws DocumentedException {
        Optional<XmlElement> filterElement = operationElement.getOnlyChildElementOptionally(FILTER);
        if (filterElement.isPresent()) {
            final List<YangInstanceIdentifier> yangInstanceIdentifiers =
                    getInstanceIdentifiersFromFilter(filterElement.get());
            // find common sub-paths if exists and return these unique sub-paths, except of first path argument
            return mergeInstanceIdentifiers(yangInstanceIdentifiers);
        } else {
            return Lists.newArrayList(ROOT);
        }
    }

    /**
     * Method takes list of instance identifiers and constructs trees with included path arguments. The aim of this step
     * is merge common path arguments. Tree ensure to us tree. For us is important return only common paths for each
     * tree and this common paths should be returned as new instance identifiers.
     * @param instanceIdentifiers list of instance identifiers
     * @return instace identifiers of common paths
     */
    private List<YangInstanceIdentifier> mergeInstanceIdentifiers(
            final List<YangInstanceIdentifier> instanceIdentifiers) {

        final HashMap<PathArgument, HashMap> pathArgumentsTree = new HashMap<>();
        // create multiple trees from given path arguments
        for (final YangInstanceIdentifier instanceIdentifier : instanceIdentifiers) {
            HashMap<PathArgument, HashMap> workingTree = pathArgumentsTree;
            for (final PathArgument pathArgument : instanceIdentifier.getPathArguments()) {
                if (!workingTree.containsKey(pathArgument)) {
                    workingTree.put(pathArgument, new HashMap<>());
                }
                workingTree = workingTree.get(pathArgument);
            }
        }

        final List<YangInstanceIdentifier> finalIdentifiers = new ArrayList<>();
        // resolve common sub-paths for each tree in the initial hash map
        pathArgumentsTree.keySet().forEach(firstLevelPathArg -> {
            @SuppressWarnings("unchecked")
            final HashMap<PathArgument, HashMap> secondLevelTree = pathArgumentsTree.get(firstLevelPathArg);
            if (secondLevelTree == null) {
                finalIdentifiers.add(YangInstanceIdentifier.create(Lists.newArrayList(firstLevelPathArg)));
            } else {
                secondLevelTree.keySet().forEach(secondLevelPathArg -> finalIdentifiers.add(
                        selectSubPaths(secondLevelTree.get(secondLevelPathArg), Lists.newArrayList(firstLevelPathArg,
                                secondLevelPathArg))));
            }
        });

        return finalIdentifiers;
    }

    /**
     * Tail recursion.
     * Traverse tree and return only paths until reach fork or last element (leaf)
     */
    private YangInstanceIdentifier selectSubPaths(final HashMap pathArgumentsTree,
                                                  final List<PathArgument> finalList) {
        if (pathArgumentsTree.keySet().size() == 1) {
            final PathArgument pathArgument = (PathArgument) pathArgumentsTree.keySet().iterator().next();
            finalList.add(pathArgument);
            return selectSubPaths((HashMap) pathArgumentsTree.get(pathArgument), finalList);
        } else {
            return YangInstanceIdentifier.create(finalList);
        }
    }

    @VisibleForTesting
    protected List<YangInstanceIdentifier> getInstanceIdentifiersFromFilter(final XmlElement filterElement) {
        return filterElement.getChildElements().stream().map(element -> {
            try {
                return validator.validate(element);
            } catch (DocumentedException exception) {
                throw new RuntimeException(exception);
            }
        }).collect(Collectors.toList());
    }

    protected static final class GetConfigExecution {

        private final Optional<Datastore> datastore;
        public GetConfigExecution(final Optional<Datastore> datastore) {
            this.datastore = datastore;
        }

        public Optional<Datastore> getDatastore() {
            return datastore;
        }

        static GetConfigExecution fromXml(final XmlElement xml, final String operationName) throws DocumentedException {
            try {
                validateInputRpc(xml, operationName);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Incorrect RPC: " + e.getMessage(), e.getErrorType(), e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            final Optional<Datastore> sourceDatastore;
            try {
                sourceDatastore = parseSource(xml);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Get-config source attribute error: " + e.getMessage(), e.getErrorType(), e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            return new GetConfigExecution(sourceDatastore);
        }

        private static Optional<Datastore> parseSource(final XmlElement xml) throws DocumentedException {
            final Optional<XmlElement> sourceElement = xml.getOnlyChildElementOptionally(XmlNetconfConstants.SOURCE_KEY,
                    XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);

            return  sourceElement.isPresent() ?
                    Optional.of(Datastore.valueOf(sourceElement.get().getOnlyChildElement().getName())) : Optional.<Datastore>absent();
        }

        private static void validateInputRpc(final XmlElement xml, final String operationName) throws DocumentedException {
            xml.checkName(operationName);
            xml.checkNamespace(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        }

    }

}
