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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.OpenApiVersionEntity;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.restconf.openapi.model.ServersEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiInputStream extends InputStream {
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final OpenApiBodyWriter writer;

    private final Deque<OpenApiEntity> stack = new ArrayDeque<>();
    private final EffectiveModelContext context;
    private final Iterator<Deque<OpenApiEntity>> componentsIterator;

    private boolean components;
    private boolean eof;

    private Reader reader;

    public OpenApiInputStream(final EffectiveModelContext context, final String openApiVersion, final Info info,
            final List<Server> servers, final List<Map<String, List<String>>> security) throws IOException {
        stream = new ByteArrayOutputStream();
        generator = new JsonFactoryBuilder().build().createGenerator(stream);
        writer = new OpenApiBodyWriter(generator);

        this.context = context;
        this.componentsIterator = new OpenApiModelComponentSnippet(context).iterator();
        stack.add(new OpenApiVersionEntity());
        stack.add(new InfoEntity(info.version(), info.title(), info.description()));
        stack.add(new ServersEntity(List.of(new ServerEntity(servers.iterator().next().url()))));
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }

        if (reader == null) {
            generator.writeStartObject();
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                if (!components) {
                    components = true;
                    generator.writeObjectFieldStart("components");
                    generator.writeObjectFieldStart("schemas");
                    generator.flush();
                    reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
                    stream.reset();
                    return reader.read();
                }
                if (componentsIterator.hasNext()) {
                    stack.addAll(componentsIterator.next());
                    continue;
                }
                eof = true;
                reader = new InputStreamReader(new ByteArrayInputStream(new byte[] { '}', '}', '}' }));
                return reader.read();
            }
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(stack.pop())));
            read = reader.read();
        }

        return read;
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, stream);
        generator.flush();
        final var bytes = stream.toByteArray();
        stream.reset();
        return bytes;
    }
}
