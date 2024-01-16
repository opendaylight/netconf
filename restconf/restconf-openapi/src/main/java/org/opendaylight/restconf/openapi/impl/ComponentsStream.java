/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.security.Http;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class ComponentsStream extends InputStream {
    private static final String BASIC_AUTH_NAME = "basicAuth";
    private static final Http OPEN_API_BASIC_AUTH = new Http("basic", null, null);

    private final Iterator<? extends Module> iterator;
    private final OpenApiBodyWriter writer;
    private final EffectiveModelContext context;
    private final JsonGenerator generator;
    private final ByteArrayOutputStream stream;
    private final boolean isForSingleModule;

    private boolean schemasWritten;
    private boolean securityWritten;
    private Reader reader;

    public ComponentsStream(final EffectiveModelContext context, final OpenApiBodyWriter writer,
        final JsonGenerator generator, final ByteArrayOutputStream stream,
        final Iterator<? extends Module> iterator, final boolean isForSingleModule) {
        this.iterator = iterator;
        this.context = context;
        this.writer = writer;
        this.generator = generator;
        this.stream = stream;
        this.isForSingleModule = isForSingleModule;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            generator.writeObjectFieldStart("components");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (!schemasWritten) {
                reader = new InputStreamReader(new SchemasStream(context, writer, generator, stream, iterator,
                    isForSingleModule), StandardCharsets.UTF_8);
                read = reader.read();
                schemasWritten = true;
                continue;
            }
            if (!securityWritten) {
                reader = new InputStreamReader(new SecuritySchemesStream(writer, Map.of(BASIC_AUTH_NAME,
                    OPEN_API_BASIC_AUTH)), StandardCharsets.UTF_8);
                read = reader.read();
                securityWritten = true;
                generator.writeEndObject();
                continue;
            }
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
            return reader.read();
        }
        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }
}
