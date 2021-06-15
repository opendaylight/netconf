/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public abstract class AbstractIdentifierAwareJaxRsProvider<T> implements MessageBodyReader<T> {

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointService mountPointService;

    protected AbstractIdentifierAwareJaxRsProvider(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService) {
        this.schemaContextHandler = schemaContextHandler;
        this.mountPointService = mountPointService;
    }

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

        final PushbackInputStream pushbackInputStream = new PushbackInputStream(entityStream);

        int firstByte = pushbackInputStream.read();
        if (firstByte == -1) {
            return emptyBody(path);
        } else {
            pushbackInputStream.unread(firstByte);
            return readBody(path, pushbackInputStream);
        }

    }

    /**
     * Create a type corresponding to an empty body.
     *
     * @param path Request path
     * @return empty body type
     */
    protected abstract T emptyBody(InstanceIdentifierContext<?> path);

    protected abstract T readBody(InstanceIdentifierContext<?> path, InputStream entityStream)
            throws WebApplicationException;


    private String getIdentifier() {
        return this.uriInfo.getPathParameters(false).getFirst("identifier");
    }

    private InstanceIdentifierContext<?> getInstanceIdentifierContext() {
        return ParserIdentifier.toInstanceIdentifier(getIdentifier(), getSchemaContext(),
                Optional.ofNullable(getMountPointService()), Optional.of(getDataBroker()));
    }

    protected UriInfo getUriInfo() {
        return this.uriInfo;
    }

    protected EffectiveModelContext getSchemaContext() {
        return schemaContextHandler.get();
    }

    protected DOMMountPointService getMountPointService() {
        return mountPointService;
    }

    protected DOMDataBroker getDataBroker() {
        return schemaContextHandler.getDataBroker();
    }

    protected boolean isPost() {
        return HttpMethod.POST.equals(this.request.getMethod());
    }

    public void setUriInfo(final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    public void setRequest(final Request request) {
        this.request = request;
    }
}
