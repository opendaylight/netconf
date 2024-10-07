/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

/**
 * A {@link MessageBodyWriter} capable of turning {@link OpenApiEntity} objects into JSON body.
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public final class OpenApiBodyWriter implements MessageBodyWriter<OpenApiEntity> {
    private final JsonFactory factory;

    public OpenApiBodyWriter(final JsonFactory factory) {
        this.factory = requireNonNull(factory);
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return OpenApiEntity.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(final OpenApiEntity entity, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        try (var generator = factory.createGenerator(entityStream)) {
            entity.generate(generator);
        }
    }
}
