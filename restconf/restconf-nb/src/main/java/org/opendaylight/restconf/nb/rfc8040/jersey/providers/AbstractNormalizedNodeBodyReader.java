/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;

/**
 * Common superclass for readers producing {@link NormalizedNodePayload}.
 */
@VisibleForTesting
public abstract class AbstractNormalizedNodeBodyReader implements MessageBodyReader<NormalizedNodePayload> {
    private final DatabindProvider databindProvider;
    private final DOMMountPointService mountPointService;

    @Context
    private UriInfo uriInfo;
    @Context
    private Request request;

    AbstractNormalizedNodeBodyReader(final DatabindProvider databindProvider,
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
    public final NormalizedNodePayload readFrom(final Class<NormalizedNodePayload> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream) throws IOException,
            WebApplicationException {
        final InstanceIdentifierContext path = ParserIdentifier.toInstanceIdentifier(
            uriInfo.getPathParameters(false).getFirst("identifier"), databindProvider.currentContext().modelContext(),
            mountPointService);

        final PushbackInputStream pushbackInputStream = new PushbackInputStream(entityStream);

        int firstByte = pushbackInputStream.read();
        if (firstByte == -1) {
            return  NormalizedNodePayload.empty(path);
        } else {
            pushbackInputStream.unread(firstByte);
            return readBody(path, pushbackInputStream);
        }
    }

    protected abstract NormalizedNodePayload readBody(InstanceIdentifierContext path, InputStream entityStream)
        throws WebApplicationException;

    final boolean isPost() {
        return HttpMethod.POST.equals(request.getMethod());
    }

    @VisibleForTesting
    public final void setUriInfo(final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @VisibleForTesting
    public final void setRequest(final Request request) {
        this.request = request;
    }
}
