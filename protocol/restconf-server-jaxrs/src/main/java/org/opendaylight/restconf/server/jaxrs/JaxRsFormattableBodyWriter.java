/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

abstract sealed class JaxRsFormattableBodyWriter implements MessageBodyWriter<JaxRsFormattableBody>
        permits JsonJaxRsFormattableBodyWriter, XmlJaxRsFormattableBodyWriter {
    @Override
    public final boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return JaxRsFormattableBody.class.isAssignableFrom(type);
    }

    @Override
    public final void writeTo(final JaxRsFormattableBody entity, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        writeTo(entity.body(), entity.prettyPrint(), requireNonNull(entityStream));
    }

    @NonNullByDefault
    abstract void writeTo(FormattableBody body, PrettyPrintParam prettyPrint, OutputStream out) throws IOException;
}
