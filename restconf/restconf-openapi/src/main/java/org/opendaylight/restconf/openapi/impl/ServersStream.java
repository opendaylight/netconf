/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.ServersEntity;

public class ServersStream extends InputStream {
    private ServersEntity entity;
    private OpenApiBodyWriter writer;

    private Reader reader;

    public ServersStream(final ServersEntity entity, final OpenApiBodyWriter writer) {
        this.entity = entity;
        this.writer = writer;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(entity)));
        }
        return reader.read();
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        final var stream = new ByteArrayOutputStream();
        writer.writeTo(entity, null, null, null, null, null, stream);
        return stream.toByteArray();
    }
}
