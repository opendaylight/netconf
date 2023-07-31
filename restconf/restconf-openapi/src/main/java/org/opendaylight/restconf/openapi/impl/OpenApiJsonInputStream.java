/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} which reports the bytes of a JSON document created through a supplying {@link EventSource}.
 */
final class OpenApiJsonInputStream extends InputStream {
    @FunctionalInterface
    interface EventSource {
        // return == has more events
        boolean nextEvent(JsonGenerator gen);
    }

    private static final class Buffer extends ByteArrayOutputStream {


        // FIXME: ineffiecient AF
        int tryRead() {
            if (count <= 0) {
                return -1;
            }

            final int ret = buf[0] & 0xff;
            count -= 1;
            System.arraycopy(buf, 1, buf, 0, count);
            return ret;
        }
    }

    private final Buffer buffer = new Buffer();
    private final EventSource source;
    private final JsonGenerator gen;

    OpenApiJsonInputStream(final JsonFactory factory, final EventSource source) throws IOException {
        this.source = requireNonNull(source);
        gen = factory.createGenerator(buffer);
    }

    @Override
    public int read() throws IOException {
        final int buffered = buffer.tryRead();
        if (buffered >= 0) {
            return buffered;
        }

        if (!source.nextEvent(gen)) {
            // FIXME: close
        }

        // TODO Auto-generated method stub
        return 0;
    }

}
