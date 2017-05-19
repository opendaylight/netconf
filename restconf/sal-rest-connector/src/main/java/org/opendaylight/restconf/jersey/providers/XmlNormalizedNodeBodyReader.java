/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.xml.sax.SAXException;

@Provider
@Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
public class XmlNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {

    private static final XMLInputFactory XIF;

    static {
        final XMLInputFactory f = XMLInputFactory.newFactory();

        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XIF = f;
    }

    @Override
    void readStream(final InputStream entityStream, final NormalizedNodeStreamWriter writer,
            final SchemaContext schemaContext, final SchemaNode parentSchema, final SchemaNode schema)
                    throws XMLStreamException,
            URISyntaxException, IOException, ParserConfigurationException, SAXException {
        final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext, schema);
        xmlParser.parse(XIF.createXMLStreamReader(entityStream));
    }
}

