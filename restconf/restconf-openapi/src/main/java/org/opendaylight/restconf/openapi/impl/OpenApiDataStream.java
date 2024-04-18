/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class OpenApiDataStream extends InputStream {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(stream);
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;
    private ReadableByteChannel channel;

    private boolean eof;

    public OpenApiDataStream(final EffectiveModelContext modelContext, final String title, final String url,
            final List<Map<String, List<String>>> security, final String deviceName, final String urlPrefix,
            final boolean isForSingleModule, final boolean includeDataStore, final PaginationService paginationContext,
            final String basePath, final Integer width, final Integer depth) throws IOException {
        final var writer = new OpenApiBodyWriter(generator, stream);
        stack.add(new OpenApiInputStream(modelContext, title, url, security, deviceName, urlPrefix, isForSingleModule,
            includeDataStore, paginationContext.getModules(), basePath, writer, stream, generator, width, depth));
        stack.add(new MetadataStream(paginationContext.getMetadata(), writer));
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
        if (eof) {
            return -1;
        }
        if (channel == null) {
            generator.writeStartObject();
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = channel.read(ByteBuffer.wrap(array, off, len));
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
                stream.reset();
                eof = true;
                return channel.read(ByteBuffer.wrap(array, off, len));
            }
            channel = Channels.newChannel(stack.pop());
            read = channel.read(ByteBuffer.wrap(array, off, len));
        }

        return read;
    }
}
