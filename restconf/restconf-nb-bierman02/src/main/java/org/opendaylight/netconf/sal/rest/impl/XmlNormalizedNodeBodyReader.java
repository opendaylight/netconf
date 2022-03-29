/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.RestUtil;
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
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Provider
@Consumes({
    Draft02.MediaTypes.DATA + RestconfService.XML,
    Draft02.MediaTypes.OPERATION + RestconfService.XML,
    MediaType.APPLICATION_XML,
    MediaType.TEXT_XML
})
public class XmlNormalizedNodeBodyReader extends AbstractIdentifierAwareJaxRsProvider
        implements MessageBodyReader<NormalizedNodeContext> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlNormalizedNodeBodyReader.class);

    public XmlNormalizedNodeBodyReader(final ControllerContext controllerContext) {
        super(controllerContext);
    }

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return true;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public NormalizedNodeContext readFrom(final Class<NormalizedNodeContext> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream) throws
        WebApplicationException {
        try {
            return readFrom(entityStream);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);
            RestconfDocumentedException.throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private NormalizedNodeContext readFrom(final InputStream entityStream) throws IOException, SAXException,
            XMLStreamException, ParserConfigurationException, URISyntaxException {
        final InstanceIdentifierContext path = getInstanceIdentifierContext();
        final Optional<InputStream> nonEmptyInputStreamOptional = RestUtil.isInputStreamEmpty(entityStream);
        if (nonEmptyInputStreamOptional.isEmpty()) {
            // represent empty nopayload input
            return new NormalizedNodeContext(path, null);
        }

        final Document doc = UntrustedXML.newDocumentBuilder().parse(nonEmptyInputStreamOptional.get());
        return parse(path, doc);
    }

    private NormalizedNodeContext parse(final InstanceIdentifierContext pathContext,final Document doc)
            throws XMLStreamException, IOException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = pathContext.getSchemaNode();
        DataSchemaNode schemaNode;
        final List<PathArgument> iiToDataList = new ArrayList<>();
        Inference inference;
        if (schemaNodeContext instanceof RpcDefinition) {
            schemaNode = ((RpcDefinition) schemaNodeContext).getInput();
            inference = pathContext.inference();
        } else if (schemaNodeContext instanceof DataSchemaNode) {
            schemaNode = (DataSchemaNode) schemaNodeContext;

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
                    final var next = current.enterChild(stack, qname);
                    checkState(next != null, "Child \"%s\" was not found in parent schema node \"%s\"", qname,
                        schemaNode);
                    iiToDataList.add(next.getIdentifier());
                    schemaNode = next.getDataSchemaNode();
                    current = next;
                } while (current.isMixin());

                inference = stack.toInference();

            } else {
                // PUT
                final QName scQName = schemaNode.getQName();
                checkState(docRootElm.equals(scQName.getLocalName()) && docRootNamespace.equals(scQName.getNamespace()),
                    "Not correct message root element \"%s\", should be \"%s\"", docRootElm, scQName);
                inference = pathContext.inference();
            }
        } else {
            throw new IllegalStateException("Unknown SchemaNode");
        }


        NormalizedNode parsed;
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        if (schemaNode instanceof ContainerLike || schemaNode instanceof ListSchemaNode
                || schemaNode instanceof LeafSchemaNode) {
            final XmlParserStream xmlParser = XmlParserStream.create(writer, inference);
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

            if (schemaNode instanceof ListSchemaNode && isPost()) {
                iiToDataList.add(parsed.getIdentifier());
            }
        } else {
            LOG.warn("Unknown schema node extension {} was not parsed", schemaNode.getClass());
            parsed = null;
        }

        return new NormalizedNodeContext(pathContext.withConcatenatedArgs(iiToDataList), parsed);
    }
}

