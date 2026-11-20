/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkedFrameDecoderTest {
    private static final String EXPECTED_MESSAGE = """
        <rpc message-id="102"
             xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
          <close-session/>
        </rpc>""";

    private static final String CHUNKED_MESSAGE_ONE = "\n#101\n" + EXPECTED_MESSAGE + "\n##\n";
    private static final String INCOMPLETE_CHUNK = "\n#4\n<rpc";

    private final ChunkedFrameDecoder decoder = new ChunkedFrameDecoder(4096);
    // One heap arena and no thread caches, so the arena counts every buffer that is not released yet
    private final PooledByteBufAllocator alloc = new PooledByteBufAllocator(false, 1, 0, 8192, 11, 0, 0, false);

    @Test
    void testMultipleChunks() {
        final var output = new ArrayList<>();
        final var input = Unpooled.copiedBuffer("""

            #4
            <rpc
            #18
             message-id="102"

            #79
                 xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <close-session/>
            </rpc>
            ##
            """.getBytes(StandardCharsets.UTF_8));
        decoder.decode(null, input, output);
        assertChunk(output);
    }

    @Test
    void testOneChunks() {
        final var output = new ArrayList<>();
        final var input = Unpooled.copiedBuffer(CHUNKED_MESSAGE_ONE.getBytes(StandardCharsets.UTF_8));
        decoder.decode(null, input, output);
        assertChunk(output);
    }

    /**
     * Write a partial NETCONF 1.1 message: header + complete DATA section, but no footer.
     *
     * <p>The DATA state calls in.readBytes(chunkSize), allocating a ByteBuf from the allocator of the input. It is
     * added as a component to the private 'chunk' CompositeByteBuf. decode() then returns waiting for the footer,
     * leaving that buffer allocated.
     */
    @Test
    void testChannelCloseReleasesIncompleteChunk() {
        final var channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(incompleteChunk());
            assertEquals(0, channel.inboundMessages().size());
            assertEquals(1, activeBuffers(), "chunk must hold the DATA section before channel close");
        } finally {
            // EmbeddedChannel.close() calls runPendingTasks() before and after, so the full pipeline
            // teardown, including handlerRemoved / handlerRemoved0, runs synchronously.
            channel.close();
        }
        // Without ChunkedFrameDecoder overriding the handlerRemoved0() the buffer stays allocated
        assertEquals(0, activeBuffers(), "chunk must be released when channel closes");
    }

    /**
     * Close the channel after a complete message, when no chunk is held.
     */
    @Test
    void testChannelCloseWithoutIncompleteChunk() {
        final var channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(Unpooled.copiedBuffer(CHUNKED_MESSAGE_ONE.getBytes(StandardCharsets.UTF_8)));
            assertChunk(new ArrayList<>(channel.inboundMessages()));
        } finally {
            channel.close();
        }
        assertDoesNotThrow(channel::checkException, "close without a held chunk must not fail");
    }

    /**
     * Write a partial NETCONF 1.1 message: header + complete DATA section, but no footer.
     *
     * <p>decode() then returns waiting for the footer, leaving 'chunk' allocated. handlerRemoved0() runs whenever the
     * decoder leaves the pipeline, not only when the channel closes. Removing the decoder while the channel stays open
     * must release 'chunk' as well.
     */
    @Test
    void testHandlerRemovalReleasesChunk() {
        final var channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(incompleteChunk());
            assertEquals(0, channel.inboundMessages().size());
            assertEquals(1, activeBuffers(), "chunk must hold the DATA section before the decoder is removed");

            channel.pipeline().remove(decoder);

            // The channel is still open, so only the removal can have released the chunk.
            assertTrue(channel.isOpen());
            assertEquals(0, activeBuffers(), "chunk must be released when the decoder is removed");
        } finally {
            channel.close();
        }
    }

    /**
     * Return the partial message from {@link #INCOMPLETE_CHUNK}, allocated by {@link #alloc}.
     *
     * <p>The decoder reads the DATA section into a buffer from the same allocator, so {@link #activeBuffers()} counts
     * it. The input itself is released once it is read completely.
     *
     * @return partial message
     */
    private ByteBuf incompleteChunk() {
        return alloc.heapBuffer().writeBytes(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@return the number of buffers from {@link #alloc} which are not released yet}
     */
    private long activeBuffers() {
        return alloc.metric().heapArenas().getFirst().numActiveAllocations();
    }

    private static void assertChunk(final List<Object> output) {
        assertEquals(1, output.size());
        final var chunk = assertInstanceOf(ByteBuf.class, output.getFirst());
        try {
            assertEquals(EXPECTED_MESSAGE, chunk.toString(StandardCharsets.UTF_8));
        } finally {
            chunk.release();
        }
    }
}
