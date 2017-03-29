/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.restconfsb.testtool.util.NormalizedNodeUtils;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Provider
@Consumes("application/yang.operation+xml")
public class XmlRpcReader implements MessageBodyReader<NormalizedNode<?, ?>> {

    @Context
    private UriInfo uri;
    private final SchemaContext schemaContext;

    public XmlRpcReader(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        //todo check
        return true;
    }

    @Override
    public NormalizedNode<?, ?> readFrom(final Class<NormalizedNode<?, ?>> type, final Type genericType, final Annotation[] annotations,
                                         final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders,
                                         final InputStream entityStream) throws IOException {
        final String rpcId = uri.getPath().replace("restconf/operations/", "");
        final String[] split = rpcId.split(":");
        final RpcDefinition rpcDefinition = NormalizedNodeUtils.findRpcDefinition(schemaContext, split[0], split[1]);
        try {
            final Element element = XmlUtil.readXmlToDocument(entityStream).getDocumentElement();
            return NormalizedNodeUtils.parseRpcInput(schemaContext, rpcDefinition, element);
        } catch (final SAXException e) {
            throw new WebApplicationException("Can't parse input", e, 500);
        }
    }

}
