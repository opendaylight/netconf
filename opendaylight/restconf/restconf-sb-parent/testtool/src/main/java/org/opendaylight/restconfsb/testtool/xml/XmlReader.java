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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconfsb.communicator.impl.common.YangInstanceIdentifierToUrlCodec;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.XmlParser;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@Provider
@Consumes("application/yang.data+xml")
public class XmlReader implements MessageBodyReader<RequestContext> {

    private final YangInstanceIdentifierToUrlCodec codec;
    private final SchemaContext schemaContext;
    @Context
    private UriInfo uri;

    public XmlReader(final SchemaContext schemaContext) {
        this.codec = new YangInstanceIdentifierToUrlCodec(schemaContext);
        this.schemaContext = schemaContext;
    }

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        //TODO implement check
        return true;
    }

    @Override
    public RequestContext readFrom(final Class<RequestContext> type, final Type genericType,
                                   final Annotation[] annotations, final MediaType mediaType,
                                   final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
            throws IOException {

        final String path = uri.getPath().replace("restconf/data", "");
        final YangInstanceIdentifier yid = codec.deserialize(path);
        final NormalizedNode<?, ?> data = new XmlParser(schemaContext).parse(yid, entityStream);
        return new RequestContext(yid, data);
    }

}
