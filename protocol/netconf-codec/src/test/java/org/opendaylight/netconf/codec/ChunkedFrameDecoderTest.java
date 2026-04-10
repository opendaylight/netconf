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

    @Test
    void testChannelCloseReleasesIncompleteChunk() throws Exception {
        // Write a partial NETCONF 1.1 message: header + complete DATA section, but no footer.
        // The DATA state calls in.readBytes(chunkSize), allocating a ByteBuf that is added as a
        // component to the private 'chunk' CompositeByteBuf.  decode() then returns waiting for
        // the footer, leaving 'chunk' alive with refCnt=1.
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

    @Test
    void testMalformedFooterReleasesChunkImmediately() throws Exception {
        // Two-step write: first complete the DATA section (which allocates 'chunk'), then send
        // a malformed footer byte that causes decode() to throw IllegalStateException.
        // We check refCnt after the exception but BEFORE channel.close() to prove that the
        // try-catch releases 'chunk' immediately, not merely deferring to teardown.
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

    @Test
    void testMalformedFooterThenValidMessage() {
        // After a framing error the decoder must start again from a clean state, so the next valid
        // message decodes correctly instead of failing on the released chunk buffer.
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
