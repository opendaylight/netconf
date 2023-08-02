/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
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
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public abstract class AbstractIdentifierAwareJaxRsProvider<T> implements MessageBodyReader<T> {

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    private final DatabindProvider databindProvider;
    private final DOMMountPointService mountPointService;

    protected AbstractIdentifierAwareJaxRsProvider(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        this.databindProvider = requireNonNull(databindProvider);
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
        final InstanceIdentifierContext path = getInstanceIdentifierContext();

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
    protected abstract T emptyBody(InstanceIdentifierContext path);

    protected abstract T readBody(InstanceIdentifierContext path, InputStream entityStream)
            throws WebApplicationException;

    private String getIdentifier() {
        return uriInfo.getPathParameters(false).getFirst("identifier");
    }

    private InstanceIdentifierContext getInstanceIdentifierContext() {
        return ParserIdentifier.toInstanceIdentifier(getIdentifier(), getSchemaContext(), getMountPointService());
    }

    protected UriInfo getUriInfo() {
        return uriInfo;
    }

    protected EffectiveModelContext getSchemaContext() {
        return databindProvider.currentContext().modelContext();
    }

    protected DOMMountPointService getMountPointService() {
        return mountPointService;
    }

    protected boolean isPost() {
        return HttpMethod.POST.equals(request.getMethod());
    }

    public void setUriInfo(final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    public void setRequest(final Request request) {
        this.request = request;
    }
}
