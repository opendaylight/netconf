/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
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
import java.util.Deque;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.PathEntity;

public final class PathStream extends InputStream {
    private final Deque<PathEntity> stack;
    private final OpenApiBodyBuffer buffer;

    private Reader reader;
    private ReadableByteChannel channel;

    public PathStream(final Deque<PathEntity> paths, final OpenApiBodyBuffer buffer) {
        stack = paths;
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            if (stack.isEmpty()) {
                return -1;
            }
            reader = new BufferedReader(new InputStreamReader(writeNextEntity(stack.pop()), StandardCharsets.UTF_8));
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                return -1;
            }
            reader = new BufferedReader(new InputStreamReader(writeNextEntity(stack.pop()), StandardCharsets.UTF_8));
            read = reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (channel == null) {
            if (stack.isEmpty()) {
                return -1;
            }
            channel = Channels.newChannel(writeNextEntity(stack.pop()));
        }
        var read = channel.read(ByteBuffer.wrap(array, off, len));
        while (read == -1) {
            if (stack.isEmpty()) {
                return -1;
            }
            channel = Channels.newChannel(writeNextEntity(stack.pop()));
            read = channel.read(ByteBuffer.wrap(array, off, len));
        }
        return read;
    }

    private ByteArrayInputStream writeNextEntity(final OpenApiEntity entity) throws IOException {
        return buffer.entityInputStream(entity);
    }
}
