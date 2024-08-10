/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import com.google.common.base.MoreObjects;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.TransformerException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * Support for {@link FramingMechanism}s.
 */
@NonNullByDefault
public abstract sealed class FramingSupport {
    private static final class Chunk extends FramingSupport {
        private static final FramingSupport DEFAULT = new Chunk(DEFAULT_CHUNK_SIZE);

        private final int chunkSize;

        Chunk(final int chunkSize) {
            if (chunkSize < MIN_CHUNK_SIZE) {
                throw new IllegalArgumentException(chunkSize + " is lower than minimum supported " + MIN_CHUNK_SIZE);
            }
            if (chunkSize > MAX_CHUNK_SIZE) {
                throw new IllegalArgumentException(chunkSize + " is lower than maximum supported " + MAX_CHUNK_SIZE);
            }
            this.chunkSize = chunkSize;
        }

        @Override
        public FramingMechanism mechanism() {
            return FramingMechanism.CHUNK;
        }

        @Override
        void writeMessage(final ByteBufAllocator alloc, final NetconfMessage message, final MessageWriter writer,
                final ByteBuf out) throws IOException, TransformerException {
            final var buffer = alloc.ioBuffer();
            try {
                // TODO: This implementation is not entirely efficient: we buffer the entire message and we frame it
                //       into individual chunks -- leading to sub-optimal memory usage.
                //       Improve this by providing an OuputStream implementation backed by 'buffer', which does not
                //       allow more buffering than chunkSize: i.e. as soon as MessageWriter invokes OutputStream.write()
                //       which would exceed chunkSize, emit the frame into 'out', clear 'buffer' and continue writing
                //       into it.

                try (var os = new ByteBufOutputStream(buffer)) {
                    writer.writeMessage(message, os);
                }

                do {
                    final int xfer = Math.min(chunkSize, buffer.readableBytes());

                    out.writeBytes(FramingParts.START_OF_CHUNK)
                        .writeBytes(String.valueOf(xfer).getBytes(StandardCharsets.US_ASCII)).writeByte('\n')
                        .writeBytes(buffer, xfer);
                } while (buffer.isReadable());
            } finally {
                buffer.release();
            }

            out.writeBytes(FramingParts.END_OF_CHUNK);
        }
    }

    private static final class EOM extends FramingSupport {
        static final FramingSupport INSTANCE = new EOM();

        private EOM() {
            // Hidden on purpose
        }

        @Override
        public FramingMechanism mechanism() {
            return FramingMechanism.EOM;
        }

        @Override
        void writeMessage(final ByteBufAllocator alloc, final NetconfMessage message, final MessageWriter writer,
                final ByteBuf out) throws IOException, TransformerException {
            try (var os = new ByteBufOutputStream(out)) {
                writer.writeMessage(message, os);
            }
            out.writeBytes(FramingParts.END_OF_MESSAGE);
        }
    }

    public static final int DEFAULT_CHUNK_SIZE = 8192;
    public static final int MIN_CHUNK_SIZE = 128;
    public static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;

    private FramingSupport() {
        // Hidden on purpose
    }

    /**
     * Return {@link FramingSupport} for {@link FramingMechanism#EOM}.
     *
     * @return A {@link FramingSupport}
     */
    public static FramingSupport eom() {
        return EOM.INSTANCE;
    }

    public static FramingSupport chunk() {
        return Chunk.DEFAULT;
    }

    public static FramingSupport chunk(final int chunkSize) {
        return chunkSize == DEFAULT_CHUNK_SIZE ? Chunk.DEFAULT : new Chunk(chunkSize);
    }

    public abstract FramingMechanism mechanism();

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(FramingSupport.class).add("mechanism", mechanism()).toString();
    }

    abstract void writeMessage(ByteBufAllocator alloc, NetconfMessage message, MessageWriter writer, ByteBuf out)
        throws IOException, TransformerException;
}
