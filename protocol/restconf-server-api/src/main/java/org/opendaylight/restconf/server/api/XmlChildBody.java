/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class XmlChildBody extends ChildBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlChildBody.class);

    public XmlChildBody(final @NonNull InputStream inputStream) {
        super(inputStream);
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    PrefixAndBody toPayload(final Data path, final InputStream inputStream) throws ServerException {
        final Document doc;
        try {
            doc = UntrustedXML.newDocumentBuilder().parse(inputStream);
        } catch (SAXException | IOException e) {
            LOG.debug("Error parsing XML input", e);
            throw newProtocolMalformedMessageServerException(path, "Invalid XML input", e);
        }

        final var pathInference = path.inference();

        final DataSchemaNode parentNode;
        if (pathInference.isEmpty()) {
            parentNode = pathInference.modelContext();
        } else {
            final var hackStack = pathInference.toSchemaInferenceStack();
            final var hackStmt = hackStack.currentStatement();
            if (hackStmt instanceof DataSchemaNode data) {
                parentNode = data;
            } else {
                throw new ServerException("Unknown SchemaNode %s", hackStmt);
            }
        }

        var schemaNode = parentNode;
        final String docRootElm = doc.getDocumentElement().getLocalName();
        final XMLNamespace docRootNamespace = XMLNamespace.of(doc.getDocumentElement().getNamespaceURI());
        final var context = pathInference.modelContext();
        final var it = context.findModuleStatements(docRootNamespace).iterator();
        if (!it.hasNext()) {
            throw new ServerException("Failed to find module for %s", docRootNamespace);
        }

        final var databind = path.databind();
        final var qname = QName.create(it.next().localQNameModule(), docRootElm);
        final var iiToDataList = ImmutableList.<PathArgument>builder();
        // FIXME: we should have this readily available: it is the last node the ApiPath->YangInstanceIdentifier parser
        //        has seen (and it should have the nodeAndStack handy
        final var nodeAndStack = databind.schemaTree().enterPath(path.instance()).orElseThrow();
        final var stack = nodeAndStack.stack();
        var current = nodeAndStack.node();
        do {
            final var next = current instanceof DataSchemaContext.Composite compositeCurrent
                ? compositeCurrent.enterChild(stack, qname) : null;
            if (next == null) {
                throw new ServerException("Child \"%s\" was not found in parent schema node \"%s\"", qname, schemaNode);
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

        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final var xmlParser = XmlParserStream.create(writer, path.databind().xmlCodecs(), stack.toInference());
        try {
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
        } catch (IllegalArgumentException | IOException | XMLStreamException e) {
            LOG.debug("Error parsing XML", e);
            throw newProtocolMalformedMessageServerException(path, "Invalid XML content", e);
        }
        var parsed = resultHolder.getResult().data();

        // When parsing an XML source with a list root node
        // the new XML parser always returns a MapNode with one MapEntryNode inside.
        // However, the old XML parser returned a MapEntryNode directly in this place.
        // Therefore we now have to extract the MapEntryNode from the parsed MapNode.
        if (parsed instanceof MapNode mapNode) {
            // extracting the MapEntryNode
            parsed = mapNode.body().iterator().next();
        }

        if (schemaNode instanceof ListSchemaNode) {
            // Supply the last item
            iiToDataList.add(parsed.name());
        }

        return new PrefixAndBody(iiToDataList.build(), parsed);
    }
}
