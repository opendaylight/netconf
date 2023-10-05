/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.util.ArrayDeque;
import java.util.Deque;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class SchemasStream extends InputStream {
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;
    private boolean eof;
    private boolean eos;

    public SchemasStream(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream) {
        this.generator = generator;
        this.stream = stream;

        // add components
        for (final var module : context.getModules()) {
            stack.add(new SchemaStream(toComponents(module), writer));
        }

        // add security schemes
        stack.add(new SecuritySchemesStream(writer));

    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (eos) {
            eof = true;
            return reader.read();
        }
        if (reader == null) {
            generator.writeObjectFieldStart("components");
            generator.writeObjectFieldStart("schemas");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.writeEndObject();
                generator.flush();
                reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
                stream.reset();
                eos = true;
                return reader.read();
            }
            reader = new InputStreamReader(stack.pop());
            read = reader.read();
        }

        return read;
    }

    private static Deque<SchemaEntity> toComponents(final Module module) {
        final var result = new ArrayDeque<SchemaEntity>();
        for (final var rpc : module.getRpcs()) {
            final var moduleName = module.getName();
            final var rpcName = rpc.getQName().getLocalName();
            final var input = new SchemaEntity(rpc.getInput(), moduleName + "_" + rpcName + "_input", "object");
            result.add(input);
            final var output = new SchemaEntity(rpc.getOutput(), moduleName + "_" + rpcName + "_output", "object");
            result.add(output);
        }

        // actions
        // child nodes
        // etc.
        return result;
    }
}
