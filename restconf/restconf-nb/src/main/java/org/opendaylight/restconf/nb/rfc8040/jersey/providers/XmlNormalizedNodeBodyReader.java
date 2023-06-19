/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Provider
@Consumes({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class XmlNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(XmlNormalizedNodeBodyReader.class);

    public XmlNormalizedNodeBodyReader(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        super(databindProvider, mountPointService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected NormalizedNodePayload readBody(final InstanceIdentifierContext path, final InputStream entityStream)
            throws WebApplicationException {
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(entityStream);
            return parse(path, doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);
            RestconfDocumentedException.throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private NormalizedNodePayload parse(final InstanceIdentifierContext pathContext, final Document doc)
            throws XMLStreamException, IOException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = pathContext.getSchemaNode();
        DataSchemaNode schemaNode;
        final List<PathArgument> iiToDataList = new ArrayList<>();
        Inference inference;
        if (schemaNodeContext instanceof OperationDefinition oper) {
            schemaNode = oper.getInput();

            final var stack = pathContext.inference().toSchemaInferenceStack();
            stack.enterSchemaTree(schemaNode.getQName());
            inference = stack.toInference();
        } else if (schemaNodeContext instanceof DataSchemaNode data) {
            schemaNode = data;

            final String docRootElm = doc.getDocumentElement().getLocalName();
            final XMLNamespace docRootNamespace = XMLNamespace.of(doc.getDocumentElement().getNamespaceURI());
            if (isPost()) {
                final var context = pathContext.getSchemaContext();
                final var it = context.findModuleStatements(docRootNamespace).iterator();
                checkState(it.hasNext(), "Failed to find module for %s", docRootNamespace);
                final var qname = QName.create(it.next().localQNameModule(), docRootElm);

                final var nodeAndStack = DataSchemaContextTree.from(context)
                    .enterPath(pathContext.getInstanceIdentifier()).orElseThrow();

                final var stack = nodeAndStack.stack();
                var current = nodeAndStack.node();
                do {
                    final var next = current instanceof DataSchemaContext.Composite compositeCurrent
                        ? compositeCurrent.enterChild(stack, qname) : null;
                    if (next == null) {
                        throw new IllegalStateException(
                            "Child \"" + qname + "\" was not found in parent schema node \"" + schemaNode + "\"");
                    }

                    // Careful about steps: for keyed list items the individual item does not have a PathArgument step,
                    // as we do not know the key values -- we supply that later
                    final var step = next.pathStep();
                    if (step != null) {
                        iiToDataList.add(step);
                    }
                    schemaNode = next.dataSchemaNode();
                    current = next;
                } while (current instanceof PathMixin);

                inference = stack.toInference();
            } else {
                // PUT
                final QName scQName = schemaNode.getQName();
                checkState(docRootElm.equals(scQName.getLocalName()) && docRootNamespace.equals(scQName.getNamespace()),
                    "Not correct message root element \"%s\", should be \"%s\"", docRootElm, scQName);
                inference = pathContext.inference();
            }
        } else {
            throw new IllegalStateException("Unknown SchemaNode " + schemaNodeContext);
        }

        NormalizedNode parsed;
        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        if (schemaNode instanceof ContainerLike || schemaNode instanceof ListSchemaNode
                || schemaNode instanceof LeafSchemaNode) {
            final XmlParserStream xmlParser = XmlParserStream.create(writer, inference);
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            parsed = resultHolder.getResult().data();

            // When parsing an XML source with a list root node
            // the new XML parser always returns a MapNode with one MapEntryNode inside.
            // However, the old XML parser returned a MapEntryNode directly in this place.
            // Therefore we now have to extract the MapEntryNode from the parsed MapNode.
            if (parsed instanceof MapNode mapNode) {
                // extracting the MapEntryNode
                parsed = mapNode.body().iterator().next();
            }

            if (schemaNode instanceof ListSchemaNode && isPost()) {
                // Supply the last item
                iiToDataList.add(parsed.name());
            }
        } else {
            LOG.warn("Unknown schema node extension {} was not parsed", schemaNode.getClass());
            parsed = null;
        }

        // FIXME: can result really be null?
        return NormalizedNodePayload.ofNullable(pathContext.withConcatenatedArgs(iiToDataList), parsed);
    }
}

