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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.PathEntity;
import org.opendaylight.restconf.openapi.model.PostEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class PathsSteam extends InputStream {
    private final Iterator<? extends Module> iterator;
    private final JsonGenerator generator;
    private final OpenApiBodyWriter writer;
    private final ByteArrayOutputStream stream;
    private final String deviceName;
    private final String urlPrefix;

    private Reader reader;
    private boolean eof;

    public PathsSteam(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream, final String deviceName,
            final String urlPrefix) {
        iterator = context.getModules().iterator();
        this.generator = generator;
        this.writer = writer;
        this.stream = stream;
        this.deviceName = deviceName;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("paths");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (iterator.hasNext()) {
                reader = new InputStreamReader(new PathStream(toPaths(iterator.next()), writer),
                    StandardCharsets.UTF_8);
                read = reader.read();
                continue;
            }
            generator.writeEndObject();
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
            eof = true;
            return reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }

    private Deque<PathEntity> toPaths(final Module module) {
        final var result = new ArrayDeque<PathEntity>();
        // RPC operations (via post) - RPCs have their own path
        for (final var rpc : module.getRpcs()) {
            // TODO connect path with payload
            final var post = new PostEntity(rpc, deviceName, module.getName());
            final String resolvedPath = "/rests/operations" + urlPrefix + "/" + module.getName() + ":"
                + rpc.getQName().getLocalName();
            final var entity = new PathEntity(resolvedPath, post);
            result.add(entity);
        }

        /**
         * TODO
         * for (final var container : module.getChildNodes()) {
         *             // get
         *             // post
         *             // put
         *             // patch
         *             // delete
         *
         *             // add path into deque
         *         }
         */
        return result;
    }
}
