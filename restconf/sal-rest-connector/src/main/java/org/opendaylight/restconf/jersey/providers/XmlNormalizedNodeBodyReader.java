/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaUtils;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Provider
@Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class XmlNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(XmlNormalizedNodeBodyReader.class);

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected NormalizedNodeContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws IOException, WebApplicationException {
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(entityStream);
            return parse(path,doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);

            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private NormalizedNodeContext parse(final InstanceIdentifierContext<?> pathContext, final Document doc)
            throws XMLStreamException, IOException, ParserConfigurationException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = pathContext.getSchemaNode();
        DataSchemaNode schemaNode;
        boolean isRpc = false;
        if (schemaNodeContext instanceof RpcDefinition) {
            schemaNode = ((RpcDefinition) schemaNodeContext).getInput();
            isRpc = true;
        } else if (schemaNodeContext instanceof DataSchemaNode) {
            schemaNode = (DataSchemaNode) schemaNodeContext;
        } else {
            throw new IllegalStateException("Unknown SchemaNode");
        }

        final String docRootElm = doc.getDocumentElement().getLocalName();
        final String docRootNamespace = doc.getDocumentElement().getNamespaceURI();
        final List<YangInstanceIdentifier.PathArgument> iiToDataList = new ArrayList<>();

        if (isPost() && !isRpc) {
            final Deque<Object> foundSchemaNodes = findPathToSchemaNodeByName(schemaNode, docRootElm, docRootNamespace);
            if (foundSchemaNodes.isEmpty()) {
                throw new IllegalStateException(String.format("Child \"%s\" was not found in parent schema node \"%s\"",
                        docRootElm, schemaNode.getQName()));
            }
            while (!foundSchemaNodes.isEmpty()) {
                final Object child = foundSchemaNodes.pop();
                if (child instanceof AugmentationSchema) {
                    final AugmentationSchema augmentSchemaNode = (AugmentationSchema) child;
                    iiToDataList.add(SchemaUtils.getNodeIdentifierForAugmentation(augmentSchemaNode));
                } else if (child instanceof DataSchemaNode) {
                    schemaNode = (DataSchemaNode) child;
                    iiToDataList.add(new YangInstanceIdentifier.NodeIdentifier(schemaNode.getQName()));
                }
            }
        // PUT
        } else if (!isRpc) {
            final QName scQName = schemaNode.getQName();
            Preconditions.checkState(
                    docRootElm.equals(scQName.getLocalName())
                            && docRootNamespace.equals(scQName.getNamespace().toASCIIString()),
                    String.format("Not correct message root element \"%s\", should be \"%s\"",
                            docRootElm, scQName));
        }

        NormalizedNode<?, ?> parsed = null;
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        if (schemaNode instanceof ContainerSchemaNode || schemaNode instanceof ListSchemaNode
                || schemaNode instanceof LeafSchemaNode) {
            final XmlParserStream xmlParser = XmlParserStream.create(writer, pathContext.getSchemaContext(),
                    schemaNode);
            xmlParser.parse(UntrustedXML.createXMLStreamReader(new StringReader(XmlUtil.toString(
                    doc.getDocumentElement()))));
            parsed = resultHolder.getResult();
            if (schemaNode instanceof  ListSchemaNode && isPost()) {
                iiToDataList.add(parsed.getIdentifier());
            }
        } else {
            LOG.warn("Unknown schema node extension {} was not parsed", schemaNode.getClass());
        }

        final YangInstanceIdentifier fullIIToData = YangInstanceIdentifier.create(Iterables.concat(
                pathContext.getInstanceIdentifier().getPathArguments(), iiToDataList));

        final InstanceIdentifierContext<? extends SchemaNode> outIIContext = new InstanceIdentifierContext<>(
                fullIIToData, pathContext.getSchemaNode(), pathContext.getMountPoint(), pathContext.getSchemaContext());

        return new NormalizedNodeContext(outIIContext, parsed);
    }

    private static Deque<Object> findPathToSchemaNodeByName(final DataSchemaNode schemaNode, final String elementName,
                                                            final String namespace) {
        final Deque<Object> result = new ArrayDeque<>();
        final ArrayList<ChoiceSchemaNode> choiceSchemaNodes = new ArrayList<>();
        final Collection<DataSchemaNode> children = ((DataNodeContainer) schemaNode).getChildNodes();
        for (final DataSchemaNode child : children) {
            if (child instanceof ChoiceSchemaNode) {
                choiceSchemaNodes.add((ChoiceSchemaNode) child);
            } else if (child.getQName().getLocalName().equalsIgnoreCase(elementName)
                    && child.getQName().getNamespace().toString().equalsIgnoreCase(namespace)) {
                // add child to result
                result.push(child);

                // find augmentation
                if (child.isAugmenting()) {
                    final AugmentationSchema augment = findCorrespondingAugment(schemaNode, child);
                    if (augment != null) {
                        result.push(augment);
                    }
                }

                // return result
                return result;
            }
        }

        for (final ChoiceSchemaNode choiceNode : choiceSchemaNodes) {
            for (final ChoiceCaseNode caseNode : choiceNode.getCases()) {
                final Deque<Object> resultFromRecursion = findPathToSchemaNodeByName(caseNode, elementName, namespace);
                if (!resultFromRecursion.isEmpty()) {
                    resultFromRecursion.push(choiceNode);
                    if (choiceNode.isAugmenting()) {
                        final AugmentationSchema augment = findCorrespondingAugment(schemaNode, choiceNode);
                        if (augment != null) {
                            resultFromRecursion.push(augment);
                        }
                    }
                    return resultFromRecursion;
                }
            }
        }
        return result;
    }

    private static AugmentationSchema findCorrespondingAugment(final DataSchemaNode parent,
                                                               final DataSchemaNode child) {
        if (parent instanceof AugmentationTarget && !(parent instanceof ChoiceSchemaNode)) {
            for (final AugmentationSchema augmentation : ((AugmentationTarget) parent).getAvailableAugmentations()) {
                final DataSchemaNode childInAugmentation = augmentation.getDataChildByName(child.getQName());
                if (childInAugmentation != null) {
                    return augmentation;
                }
            }
        }
        return null;
    }
}

