/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * An XML-encoded {@link ResourceBody}.
 */
public final class XmlResourceBody extends ResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlResourceBody.class);

    public XmlResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final InputStream inputStream, final Inference inference, final PathArgument name,
            final NormalizedNodeStreamWriter writer) throws IOException {
        try (var xmlParser = XmlParserStream.create(writer, inference)) {
            final var doc = UntrustedXML.newDocumentBuilder().parse(inputStream);
            final var docRoot = doc.getDocumentElement();
            final var docRootName = docRoot.getLocalName();
            final var docRootNs = docRoot.getNamespaceURI();
            final var qname = name.getNodeType();
            final var pathName = qname.getLocalName();
            final var pathNs = qname.getNamespace().toString();
            if (!docRootName.equals(pathName) || !docRootNs.equals(pathNs)) {
                throw new RestconfDocumentedException("Incorrect message root element (" + docRootNs + ")" + docRootName
                    + ", should be (" + pathNs + ")" + pathName, ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }

            xmlParser.traverse(new DOMSource(docRoot));
        } catch (SAXException | XMLStreamException e) {
            LOG.debug("Error parsing XML input", e);
            throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }
}
