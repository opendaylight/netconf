/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.xml.transform.TransformerException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support for {@link FramingMechanism}s. Instances are available via {@link #eom()} and {@link #chunk()} static factory
 * methods.
 */
public abstract sealed class FramingSupport {
    private static final class Chunk extends FramingSupport {
        static final @NonNull Chunk DEFAULT = new Chunk(DEFAULT_CHUNK_SIZE);

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
            final var allocated = alloc.ioBuffer();
            try {
                final var maxCapacity = allocated.maxCapacity();
                final ByteBuf buffer;
                final int size;

                // Safety checks so we do not run into trouble with chunk size exceeding buffer maximum size
                if (maxCapacity < chunkSize) {
                    LOG.debug("Allocated buffer cannot support chunk size {}", chunkSize);
                    if (maxCapacity >= MIN_CHUNK_SIZE) {
                        LOG.debug("Using chunk size {}", maxCapacity);
                        buffer = allocated;
                        size = maxCapacity;
                    } else {
                        LOG.debug("Using chunk size {} with unpooled on-heap buffer", MIN_CHUNK_SIZE);
                        buffer = Unpooled.buffer(MIN_CHUNK_SIZE);
                        size = MIN_CHUNK_SIZE;
                    }
                } else {
                    buffer = allocated;
                    size = chunkSize;
                }

                try (var os = new ChunkOutputStream(out, buffer, size)) {
                    // let the writer do its thing first ...
                    writer.writeMessage(message, os);
                    // ... and nothing bad happens, also make sure to send anything that remains in the buffer
                    os.flushChunk();
                }
            } finally {
                allocated.release();
            }

            out.writeBytes(FramingParts.END_OF_CHUNKS);
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper).add("chunkSize", chunkSize);
        }
    }

    private static final class ChunkOutputStream extends OutputStream {
        private final ByteBuf buffer;
        private final ByteBuf out;
        private final int chunkSize;

        ChunkOutputStream(final ByteBuf out, final ByteBuf buffer, final int chunkSize) {
            this.out = requireNonNull(out);
            this.buffer = requireNonNull(buffer);
            this.chunkSize = chunkSize;
        }

        @Override
        public void write(final int value) throws IOException {
            final var size = size();
            if (size == chunkSize) {
                sendChunk(size);
            }
            buffer.writeByte(value);
        }

        @Override
        public void write(final byte[] bytes, final int off, final int len) throws IOException {
            Objects.checkFromIndexSize(off, len, bytes.length);
            if (len == 0) {
                return;
            }

            int from = off;
            int remaining = len;
            int available = chunkSize - size();
            do {
                if (available == 0) {
                    sendChunk(chunkSize);
                    available = chunkSize;
                }

                final int xfer = Math.min(remaining, available);
                buffer.writeBytes(bytes, from, xfer);
                from += xfer;
                remaining -= xfer;
                available -= xfer;
            } while (remaining != 0);
        }

        void flushChunk() {
            final var size = size();
            if (size != 0) {
                sendChunk(size);
            }
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("chunkSize", chunkSize).add("pending", size()).toString();
        }

        private void sendChunk(final int size) {
            out.writeBytes(FramingParts.START_OF_CHUNK)
                .writeBytes(Integer.toString(size).getBytes(StandardCharsets.US_ASCII)).writeByte('\n')
                .writeBytes(buffer);
            buffer.clear();
        }

        private int size() {
            // buffer.readableBytes(), but we know readerIndex() is 0, so this is faster
            return buffer.writerIndex();
        }
    }

    private static final class EOM extends FramingSupport {
        static final @NonNull EOM INSTANCE = new EOM();

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

    private static final Logger LOG = LoggerFactory.getLogger(FramingSupport.class);

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
    public static @NonNull FramingSupport eom() {
        return EOM.INSTANCE;
    }

    /**
     * Return {@link FramingSupport} for {@link FramingMechanism#CHUNK} producing {@value #DEFAULT_CHUNK_SIZE}-byte
     * chunks.
     *
     * @return A {@link FramingSupport}
     */
    public static @NonNull FramingSupport chunk() {
        return Chunk.DEFAULT;
    }

    /**
     * Return {@link FramingSupport} for {@link FramingMechanism#CHUNK} producing chunks of specified size.
     *
     * @return A {@link FramingSupport}
     * @throws IllegalArgumentException if {@code chunkSize} is not valid
     */
    public static @NonNull FramingSupport chunk(final int chunkSize) {
        return chunkSize == DEFAULT_CHUNK_SIZE ? Chunk.DEFAULT : new Chunk(chunkSize);
    }

    /**
     * Return the {@link FramingMechanism} of this object supports.
     *
     * @return A {@link FramingMechanism}
     */
    public abstract @NonNull FramingMechanism mechanism();

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(FramingSupport.class)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("mechanism", mechanism());
    }

    abstract void writeMessage(ByteBufAllocator alloc, NetconfMessage message, MessageWriter writer, ByteBuf out)
        throws IOException, TransformerException;

}
