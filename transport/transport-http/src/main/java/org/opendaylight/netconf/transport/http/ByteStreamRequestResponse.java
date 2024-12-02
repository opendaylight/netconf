/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link AbstractFiniteResponse} additionally holding a body representation which can be turned into a byte stream.
 */
@NonNullByDefault
public abstract class ByteStreamRequestResponse extends AbstractFiniteResponse {
    protected ByteStreamRequestResponse(final HttpResponseStatus status) {
        super(status);
    }

    @Override
    public final ReadyResponse toReadyResponse(final ByteBufAllocator alloc) throws IOException {
        final var content = alloc.buffer();
        try (var out = new ByteBufOutputStream(content)) {
            writeBody(out);
        } catch (IOException e) {
            content.release();
            throw e;
        }
        return toReadyResponse(content);
    }

    protected abstract ReadyResponse toReadyResponse(ByteBuf content);

    protected abstract void writeBody(OutputStream out) throws IOException;
}
