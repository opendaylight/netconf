/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;

class DeserializerExceptionHandlerTest {
    private DeserializerExceptionHandler handler;
    private ChannelFuture channelFuture;
    private ChannelHandlerContext context;
    private Channel channel;

    @BeforeEach
    void setUp() {
        handler = new DeserializerExceptionHandler();
        context = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);
        doReturn(channel).when(context).channel();
        channelFuture = mock(ChannelFuture.class);
        doReturn(channelFuture).when(channelFuture).addListener(any());
        doReturn(channelFuture).when(channel).writeAndFlush(any());
    }

    @Test
    void testExceptionCaught() {
        handler.exceptionCaught(context, new Exception());
        verify(context, times(1)).channel();
        verify(channel).writeAndFlush(any(NetconfMessage.class));
    }

    /**
     * RFC 6242 section 4.2: after a chunked framing error the session MUST be closed. The peer receives the rpc-error
     * reply first, then the channel is closed.
     */
    @Test
    void testFramingErrorSendsReplyAndClosesChannel() {
        final var embeddedChannel = new EmbeddedChannel(new ChunkedFrameDecoder(4096), handler);
        try {
            // A chunk header must start with '\n'
            embeddedChannel.writeInbound(Unpooled.copiedBuffer("X".getBytes(StandardCharsets.UTF_8)));

            assertMalformedMessageReply(embeddedChannel);
            assertFalse(embeddedChannel.isOpen(), "channel must be closed after a chunked framing error");
        } finally {
            embeddedChannel.close();
        }
    }

    /**
     * The channel must not be closed before the rpc-error reply is written. Over SSH the write completes later, and a
     * close before that would drop the reply.
     */
    @Test
    void testFramingErrorClosesChannelOnlyAfterReplyIsWritten() {
        final var heldWrite = new WriteHoldingHandler();
        final var embeddedChannel = new EmbeddedChannel(heldWrite, new ChunkedFrameDecoder(4096), handler);
        try {
            // A chunk header must start with '\n'
            embeddedChannel.writeInbound(Unpooled.copiedBuffer("X".getBytes(StandardCharsets.UTF_8)));

            assertInstanceOf(NetconfMessage.class, heldWrite.heldMsg);
            assertTrue(embeddedChannel.isOpen(), "channel must stay open until the error reply is written");

            heldWrite.heldPromise.setSuccess();
            assertFalse(embeddedChannel.isOpen(), "channel must be closed once the error reply is written");
        } finally {
            embeddedChannel.close();
        }
    }

    /**
     * Any other error is answered with an rpc-error reply and the session stays open.
     */
    @Test
    void testOtherErrorSendsReplyAndKeepsChannelOpen() {
        final var embeddedChannel = new EmbeddedChannel(handler);
        try {
            embeddedChannel.pipeline().fireExceptionCaught(new IllegalStateException("Malformed XML"));

            assertMalformedMessageReply(embeddedChannel);
            assertTrue(embeddedChannel.isOpen(), "channel must stay open after an error other than a framing error");
        } finally {
            embeddedChannel.close();
        }
    }

    private static void assertMalformedMessageReply(final EmbeddedChannel embeddedChannel) {
        final var reply = assertInstanceOf(NetconfMessage.class, embeddedChannel.readOutbound());
        final var errorTags = reply.getDocument().getElementsByTagNameNS(NamespaceURN.BASE, "error-tag");
        assertEquals(1, errorTags.getLength());
        assertEquals("malformed-message", errorTags.item(0).getTextContent());
    }

    /**
     * Holds back a write instead of passing it on, so that a test decides when the write completes.
     */
    private static final class WriteHoldingHandler extends ChannelOutboundHandlerAdapter {
        private Object heldMsg;
        private ChannelPromise heldPromise;

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            heldMsg = msg;
            heldPromise = promise;
        }
    }
}
