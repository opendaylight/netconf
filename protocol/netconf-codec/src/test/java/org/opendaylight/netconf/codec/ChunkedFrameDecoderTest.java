/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
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
     * <p>The DATA state calls in.readBytes(chunkSize), allocating a ByteBuf that is added as a component to the private
     * 'chunk' CompositeByteBuf. decode() then returns waiting for the footer, leaving 'chunk' alive with refCnt=1.
     */
    @Test
    void testChannelCloseReleasesIncompleteChunk() throws Exception {
        final var channel = new EmbeddedChannel(decoder);
        final CompositeByteBuf capturedChunk;
        try {
            channel.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, channel.inboundMessages().size());

            // Capture the internal 'chunk' buffer field via reflection so we can track its refCnt after close.
            final var chunkField = ChunkedFrameDecoder.class.getDeclaredField("chunk");
            chunkField.setAccessible(true);
            capturedChunk = (CompositeByteBuf) chunkField.get(decoder);
            assertNotNull(capturedChunk, "chunk must be allocated after reading the DATA section");
            assertEquals(1, capturedChunk.refCnt(), "chunk must be live (refCnt=1) before channel close");
        } finally {
            // EmbeddedChannel.close() calls runPendingTasks() before and after, so the full pipeline
            // teardown, including handlerRemoved / handlerRemoved0, runs synchronously.
            channel.close();
        }
        // Without ChunkedFrameDecoder overriding the handlerRemoved0() the refCnt stays 1
        // Releasing the chunk buffer in handlerRemoved0() refCnt drops the refCnt to 0
        assertEquals(0, capturedChunk.refCnt(), "chunk must be released when channel closes");
    }

    /**
     * Write a partial NETCONF 1.1 message: header + complete DATA section, but no footer. decode() then returns waiting
     * for the footer, leaving 'chunk' alive with refCnt=1.
     *
     * <p>handlerRemoved0() runs whenever the decoder leaves the pipeline, not only when the channel closes. Removing
     * the decoder while the channel stays open must release 'chunk' as well.
     */
    @Test
    void testHandlerRemovalReleasesChunk() throws Exception {
        final var channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, channel.inboundMessages().size());

            // Capture the internal 'chunk' buffer field via reflection so we can track its refCnt after removal.
            final var chunkField = ChunkedFrameDecoder.class.getDeclaredField("chunk");
            chunkField.setAccessible(true);
            final var capturedChunk = (CompositeByteBuf) chunkField.get(decoder);
            assertNotNull(capturedChunk, "chunk must be allocated after reading the DATA section");
            assertEquals(1, capturedChunk.refCnt(), "chunk must be live before the decoder is removed");

            channel.pipeline().remove(decoder);

            // The channel is still open, so only the removal can have released the chunk.
            assertTrue(channel.isOpen());
            assertEquals(0, capturedChunk.refCnt(), "chunk must be released when the decoder is removed");
        } finally {
            channel.close();
        }
    }

    /**
     * Two-step write: first complete the DATA section (which allocates 'chunk'), then send a malformed footer byte that
     * causes decode() to throw IllegalStateException.
     *
     * <p>We check refCnt after the exception but BEFORE channel.close() to prove that the try-catch releases 'chunk'
     * immediately, not merely deferring to teardown.
     */
    @Test
    void testMalformedFooterReleasesChunkImmediately() throws Exception {
        final var channel = new EmbeddedChannel(decoder);
        try {
            // Step 1: header + complete data, state lands at FOOTER_ONE, chunk allocated with refCnt=1.
            channel.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8)));

            final var chunkField = ChunkedFrameDecoder.class.getDeclaredField("chunk");
            chunkField.setAccessible(true);
            final var capturedChunk = (CompositeByteBuf) chunkField.get(decoder);
            assertNotNull(capturedChunk, "chunk must be allocated after reading the DATA section");
            assertEquals(1, capturedChunk.refCnt(), "chunk must be live (refCnt=1) before the malformed byte");

            // Step 2: '\n' advances FOOTER_ONE to FOOTER_TWO; 'X' fails checkHash and throws.
            final var ex = assertThrows(DecoderException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer("\nX".getBytes(StandardCharsets.UTF_8))));
            assertInstanceOf(IllegalStateException.class, ex.getCause());

            // Assert BEFORE channel.close(). If we closed first, ChunkedFrameDecoder.handlerRemoved0() would release
            // chunk regardless of whether the try-catch is present, hiding the bug.
            // Without the try-catch in decode() the refCnt stays 1
            // With the try-catch in decode() the refCnt drops to 0
            assertEquals(0, capturedChunk.refCnt(), "chunk must be released immediately when decode() throws");
        } finally {
            channel.close();
        }
    }

    /**
     * Two-step write: first complete the DATA section (which allocates 'chunk'), then send the header of a next chunk
     * whose size exceeds the maximum of 4096.
     *
     * <p>We check refCnt after the exception but BEFORE channel.close() to prove that the try-catch releases 'chunk'
     * immediately, not merely deferring to teardown.
     */
    @Test
    void testOversizedNextChunkReleasesChunk() throws Exception {
        final var channel = new EmbeddedChannel(decoder);
        try {
            // Step 1: header + complete data, state lands at FOOTER_ONE, chunk allocated with refCnt=1.
            channel.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8)));

            final var chunkField = ChunkedFrameDecoder.class.getDeclaredField("chunk");
            chunkField.setAccessible(true);
            final var capturedChunk = (CompositeByteBuf) chunkField.get(decoder);
            assertNotNull(capturedChunk, "chunk must be allocated after reading the DATA section");
            assertEquals(1, capturedChunk.refCnt(), "chunk must be live (refCnt=1) before the oversized chunk header");

            // Step 2: "\n#5" starts the next chunk. Each '0' grows the size, and at 5000 checkChunkSize() throws.
            final var ex = assertThrows(DecoderException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer("\n#5000\n".getBytes(StandardCharsets.UTF_8))));
            final var cause = assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("Chunk size 5000 exceeds maximum 4096", cause.getMessage());

            // Assert BEFORE channel.close(). If we closed first, ChunkedFrameDecoder.handlerRemoved0() would release
            // chunk regardless of whether the try-catch is present, hiding the bug.
            assertEquals(0, capturedChunk.refCnt(), "chunk must be released when the chunk size is too large");
        } finally {
            channel.close();
        }
    }

    /**
     * After a framing error the decoder must start again from a clean state, so the next valid message decodes
     * correctly instead of failing on the released chunk buffer.
     */
    @Test
    void testMalformedFooterThenValidMessage() {
        final var channel = new EmbeddedChannel(decoder);
        try {
            // Step 1: header + complete data, state lands at FOOTER_ONE.
            channel.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNK.getBytes(StandardCharsets.UTF_8)));

            // Step 2: 'X' is not a valid footer byte. decode() releases the chunk and resets its state.
            final var ex = assertThrows(DecoderException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer("\nX".getBytes(StandardCharsets.UTF_8))));
            assertInstanceOf(IllegalStateException.class, ex.getCause());

            // Step 3: a complete valid message is decoded as a whole.
            channel.writeInbound(Unpooled.copiedBuffer(CHUNKED_MESSAGE_ONE.getBytes(StandardCharsets.UTF_8)));
            assertChunk(new ArrayList<>(channel.inboundMessages()));
        } finally {
            channel.close();
        }
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
