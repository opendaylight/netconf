/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Provider
@Consumes({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class XmlNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(XmlNormalizedNodeBodyReader.class);

    public XmlNormalizedNodeBodyReader(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService) {
        super(schemaContextHandler, mountPointService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected NormalizedNodeContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws WebApplicationException {
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(entityStream);
            return parse(path,doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);
            RestconfDocumentedException.throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private NormalizedNodeContext parse(final InstanceIdentifierContext<?> pathContext, final Document doc)
            throws XMLStreamException, IOException, ParserConfigurationException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = pathContext.getSchemaNode();
        DataSchemaNode schemaNode;
        final boolean isOperation;
        if (schemaNodeContext instanceof OperationDefinition) {
            schemaNode = ((OperationDefinition) schemaNodeContext).getInput();
            isOperation = true;
        } else if (schemaNodeContext instanceof DataSchemaNode) {
            schemaNode = (DataSchemaNode) schemaNodeContext;
            isOperation = false;
        } else {
            throw new IllegalStateException("Unknown SchemaNode " + schemaNodeContext);
        }

        final String docRootElm = doc.getDocumentElement().getLocalName();
        final String docRootNamespace = doc.getDocumentElement().getNamespaceURI();
        final List<YangInstanceIdentifier.PathArgument> iiToDataList = new ArrayList<>();

        if (isPost() && !isOperation) {
            final Deque<Object> foundSchemaNodes = findPathToSchemaNodeByName(schemaNode, docRootElm, docRootNamespace);
            if (foundSchemaNodes.isEmpty()) {
                throw new IllegalStateException(String.format("Child \"%s\" was not found in parent schema node \"%s\"",
                        docRootElm, schemaNode.getQName()));
            }
            while (!foundSchemaNodes.isEmpty()) {
                final Object child = foundSchemaNodes.pop();
                if (child instanceof AugmentationSchemaNode) {
                    final AugmentationSchemaNode augmentSchemaNode = (AugmentationSchemaNode) child;
                    iiToDataList.add(DataSchemaContextNode.augmentationIdentifierFrom(augmentSchemaNode));
                } else if (child instanceof DataSchemaNode) {
                    schemaNode = (DataSchemaNode) child;
                    iiToDataList.add(new YangInstanceIdentifier.NodeIdentifier(schemaNode.getQName()));
                }
            }
        // PUT
        } else if (!isOperation) {
            final QName scQName = schemaNode.getQName();
            checkState(docRootElm.equals(scQName.getLocalName())
                && docRootNamespace.equals(scQName.getNamespace().toString()),
                "Not correct message root element \"%s\", should be \"%s\"", docRootElm, scQName);
        }

        NormalizedNode parsed;
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        if (schemaNode instanceof ContainerLike || schemaNode instanceof ListSchemaNode
                || schemaNode instanceof LeafSchemaNode) {
            final XmlParserStream xmlParser = XmlParserStream.create(writer, SchemaInferenceStack.ofInstantiatedPath(
                pathContext.getSchemaContext(), schemaNode.getPath()).toInference());
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            parsed = resultHolder.getResult();

            // When parsing an XML source with a list root node
            // the new XML parser always returns a MapNode with one MapEntryNode inside.
            // However, the old XML parser returned a MapEntryNode directly in this place.
            // Therefore we now have to extract the MapEntryNode from the parsed MapNode.
            if (parsed instanceof MapNode) {
                final MapNode mapNode = (MapNode) parsed;
                // extracting the MapEntryNode
                parsed = mapNode.body().iterator().next();
            }

            if (schemaNode instanceof  ListSchemaNode && isPost()) {
                iiToDataList.add(parsed.getIdentifier());
            }
        } else {
            LOG.warn("Unknown schema node extension {} was not parsed", schemaNode.getClass());
            parsed = null;
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
        for (final DataSchemaNode child : ((DataNodeContainer) schemaNode).getChildNodes()) {
            if (child instanceof ChoiceSchemaNode) {
                choiceSchemaNodes.add((ChoiceSchemaNode) child);
            } else if (child.getQName().getLocalName().equalsIgnoreCase(elementName)
                    && child.getQName().getNamespace().toString().equalsIgnoreCase(namespace)) {
                // add child to result
                result.push(child);

                // find augmentation
                if (child.isAugmenting()) {
                    final AugmentationSchemaNode augment = findCorrespondingAugment(schemaNode, child);
                    if (augment != null) {
                        result.push(augment);
                    }
                }

                // return result
                return result;
            }
        }

        for (final ChoiceSchemaNode choiceNode : choiceSchemaNodes) {
            for (final CaseSchemaNode caseNode : choiceNode.getCases()) {
                final Deque<Object> resultFromRecursion = findPathToSchemaNodeByName(caseNode, elementName, namespace);
                if (!resultFromRecursion.isEmpty()) {
                    resultFromRecursion.push(choiceNode);
                    if (choiceNode.isAugmenting()) {
                        final AugmentationSchemaNode augment = findCorrespondingAugment(schemaNode, choiceNode);
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

    private static AugmentationSchemaNode findCorrespondingAugment(final DataSchemaNode parent,
                                                               final DataSchemaNode child) {
        if (parent instanceof AugmentationTarget && !(parent instanceof ChoiceSchemaNode)) {
            for (AugmentationSchemaNode augmentation : ((AugmentationTarget) parent).getAvailableAugmentations()) {
                if (augmentation.dataChildByName(child.getQName()) != null) {
                    return augmentation;
                }
            }
        }
        return null;
    }
}

