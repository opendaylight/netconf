/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.DataPostPath;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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

    public XmlChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    PrefixAndBody toPayload(final DataPostPath path, final InputStream inputStream) throws RestconfDocumentedException {
        try {
            return parse(path, UntrustedXML.newDocumentBuilder().parse(inputStream));
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);
            throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private static @NonNull PrefixAndBody parse(final DataPostPath path, final Document doc)
            throws XMLStreamException, IOException, SAXException, URISyntaxException {
        final var pathInference = path.inference();

        final DataSchemaNode parentNode;
        if (pathInference.isEmpty()) {
            parentNode = pathInference.getEffectiveModelContext();
        } else {
            final var hackStack = pathInference.toSchemaInferenceStack();
            final var hackStmt = hackStack.currentStatement();
            if (hackStmt instanceof DataSchemaNode data) {
                parentNode = data;
            } else {
                throw new IllegalStateException("Unknown SchemaNode " + hackStmt);
            }
        }

        var schemaNode = parentNode;
        final String docRootElm = doc.getDocumentElement().getLocalName();
        final XMLNamespace docRootNamespace = XMLNamespace.of(doc.getDocumentElement().getNamespaceURI());
        final var context = pathInference.getEffectiveModelContext();
        final var it = context.findModuleStatements(docRootNamespace).iterator();
        checkState(it.hasNext(), "Failed to find module for %s", docRootNamespace);
        final var qname = QName.create(it.next().localQNameModule(), docRootElm);

        final var iiToDataList = ImmutableList.<PathArgument>builder();
        // FIXME: we should have this readily available: it is the last node the ApiPath->YangInstanceIdentifier parser
        //        has seen (and it should have the nodeAndStack handy
        final var nodeAndStack = path.databind().schemaTree().enterPath(path.instance()).orElseThrow();
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

        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final var xmlParser = XmlParserStream.create(writer, path.databind().xmlCodecs(), stack.toInference());
        xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
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
