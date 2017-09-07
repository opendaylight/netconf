/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.RestConnectorProvider;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;

public abstract class AbstractIdentifierAwareJaxRsProvider<T> implements MessageBodyReader<T> {

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    @Override
    public final boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return true;
    }

    @Override
    public final T readFrom(final Class<T> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream) throws IOException,
            WebApplicationException {
        final InstanceIdentifierContext<?> path = getInstanceIdentifierContext();
        if (entityStream.available() < 1) {
            return emptyBody(path);
        }

        return readBody(path, entityStream);
    }

    /**
     * Create a type corresponding to an empty body.
     *
     * @param path Request path
     * @return empty body type
     */
    protected abstract T emptyBody(InstanceIdentifierContext<?> path);

    protected abstract T readBody(InstanceIdentifierContext<?> path, InputStream entityStream)
            throws IOException, WebApplicationException;


    private String getIdentifier() {
        return this.uriInfo.getPathParameters(false).getFirst(RestconfConstants.IDENTIFIER);
    }

    private InstanceIdentifierContext<?> getInstanceIdentifierContext() {
        return ParserIdentifier.toInstanceIdentifier(
                getIdentifier(),
                SchemaContextHandler.getActualSchemaContext(),
                Optional.of(RestConnectorProvider.getMountPointService()));
    }

    protected UriInfo getUriInfo() {
        return this.uriInfo;
    }

    protected boolean isPost() {
        return HttpMethod.POST.equals(this.request.getMethod());
    }

    void setUriInfo(final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    void setRequest(final Request request) {
        this.request = request;
    }
}
