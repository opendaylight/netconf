/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.common.base.Optional;
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
import org.opendaylight.netconf.sal.rest.api.RestconfConstants;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;

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
            throws IOException, WebApplicationException;


    private String getIdentifier() {
        return this.uriInfo.getPathParameters(false).getFirst(RestconfConstants.IDENTIFIER);
    }

    private InstanceIdentifierContext<?> getInstanceIdentifierContext() {
        return ParserIdentifier.toInstanceIdentifier(
                getIdentifier(),
                ControllerContext.getInstance().getGlobalSchema(),
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
