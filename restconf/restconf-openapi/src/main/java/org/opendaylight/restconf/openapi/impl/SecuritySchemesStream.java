/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.SecuritySchemesEntity;
import org.opendaylight.restconf.openapi.model.security.SecuritySchemeObject;

public final class SecuritySchemesStream extends InputStream {
    private final OpenApiBodyBuffer buffer;
    private final SecuritySchemesEntity securitySchemesEntity;

    private Reader reader;
    private ReadableByteChannel channel;

    public SecuritySchemesStream(final OpenApiBodyBuffer buffer,
            final Map<String, SecuritySchemeObject> securitySchemes) {
        this.buffer = buffer;
        securitySchemesEntity = new SecuritySchemesEntity(securitySchemes);
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(
                new InputStreamReader(writeNextEntity(securitySchemesEntity), StandardCharsets.UTF_8));
        }
        return reader.read();
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (channel == null) {
            channel = Channels.newChannel(writeNextEntity(securitySchemesEntity));
        }
        return channel.read(ByteBuffer.wrap(array, off, len));
    }

    private ByteArrayInputStream writeNextEntity(final OpenApiEntity next) throws IOException {
        return buffer.entityInputStream(next);
    }
}
