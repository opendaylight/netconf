/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

abstract class AbstractNormalizedNodeBodyWriter implements MessageBodyWriter<NormalizedNodePayload> {
    @Override
    public final boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return type.equals(NormalizedNodePayload.class);
    }

    @Override
    public final void writeTo(final NormalizedNodePayload context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        final var data = context.getData();
        if (data == null) {
            return;
        }
        if (httpHeaders != null) {
            for (var entry : context.getNewHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        writeTo(context.getInstanceIdentifierContext(), context.getWriterParameters(), data,
            requireNonNull(entityStream));
    }

    abstract void writeTo(@NonNull InstanceIdentifierContext context, @NonNull QueryParameters writerParameters,
        @NonNull NormalizedNode data, @NonNull OutputStream entityStream) throws IOException;
}
