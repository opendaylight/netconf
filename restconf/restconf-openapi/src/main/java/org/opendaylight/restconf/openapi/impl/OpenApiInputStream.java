/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiVersionEntity;
import org.opendaylight.restconf.openapi.model.SecurityEntity;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.restconf.openapi.model.ServersEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class OpenApiInputStream extends InputStream {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(stream);
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;

    private boolean eof;

    public OpenApiInputStream(final EffectiveModelContext context, final String title, final String url,
            final List<Map<String, List<String>>> security, final String deviceName, final String urlPrefix,
            final boolean isForSingleModule, final boolean includeDataStore, final Collection<? extends Module> modules)
            throws IOException {
        final OpenApiBodyWriter writer = new OpenApiBodyWriter(generator, stream);
        stack.add(new OpenApiVersionStream(new OpenApiVersionEntity(), writer));
        stack.add(new InfoStream(new InfoEntity(title), writer));
        stack.add(new ServersStream(new ServersEntity(List.of(new ServerEntity(url))), writer));
        stack.add(new PathsStream(context, writer, generator, stream, deviceName, urlPrefix, isForSingleModule,
            includeDataStore, modules.iterator()));
        stack.add(new ComponentsStream(context, writer, generator, stream, modules.iterator()));
        stack.add(new SecurityStream(writer, new SecurityEntity(security)));
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeStartObject();
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
                stream.reset();
                eof = true;
                return reader.read();
            }
            reader = new BufferedReader(new InputStreamReader(stack.pop(), StandardCharsets.UTF_8));
            read = reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }
}
