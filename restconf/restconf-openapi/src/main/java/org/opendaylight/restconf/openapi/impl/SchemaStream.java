/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Deque;
import java.util.Iterator;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.SchemaEntity;

public class SchemaStream extends InputStream {
    private final Iterator<SchemaEntity> iterator;
    private final Deque<SchemaEntity> paths;
    private final OpenApiBodyWriter writer;

    private Reader reader;

    public SchemaStream(final Deque<SchemaEntity> paths, final OpenApiBodyWriter writer) {
        this.iterator = paths.iterator();
        this.paths = paths;
        this.writer = writer;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            if (iterator.hasNext()) {
                reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(iterator.next())));
            } else {
                return -1;
            }
        }

        final var read = reader.read();
        if (read == -1) {
            if (iterator.hasNext()) {
                reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(iterator.next())));
            }
            return -1;
        }

        return reader.read();
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, null);
        return writer.readFrom();
    }
}
