/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.common.handler.EXIParameters;
import org.opendaylight.netconf.common.handler.NetconfEXICodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NetconfClientSessionTest {
    @Mock
    private ChannelHandler channelHandler;
    @Mock
    private Channel channel;
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private MessageWriter messageWriter;

    @Test
    void testNetconfClientSession() throws Exception {
        final var sessId = new SessionIdType(Uint32.valueOf(20));
        final var caps = List.of("cap1", "cap2");
        final var codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        final var encoder = new MessageEncoder(messageWriter);

        doReturn(pipeline).when(channel).pipeline();
        doReturn(channelHandler).when(pipeline).replace(eq(MessageDecoder.class), anyString(),
            any(MessageDecoder.class));
        doReturn(encoder).when(pipeline).get(MessageEncoder.class);

        final var session = new NetconfClientSession(sessionListener, channel, sessId, caps);
        session.addExiHandlers(codec.newMessageDecoder(), codec.newMessageWriter());
        final var started = encoder.writer();
        assertNotSame(messageWriter, started);

        session.stopExiCommunication();
        final var stopped = encoder.writer();
        assertNotSame(messageWriter, stopped);
        assertNotSame(started, stopped);

        assertEquals(caps, session.getServerCapabilities());
        assertEquals(session, session.thisInstance());

        verify(pipeline, times(2)).replace(eq(MessageDecoder.class), anyString(), any(MessageDecoder.class));
    }

    /**
     * RFC 6242 section 4.2: after a chunked framing error the session MUST be closed.
     */
    @Test
    void testFramingErrorClosesChannel() {
        final var embeddedChannel = new EmbeddedChannel();
        try {
            final var session = addSession(embeddedChannel);

            // A chunk header must start with '\n'. The error must not reach the end of the pipeline, which would make
            // writeInbound() throw.
            embeddedChannel.writeInbound(Unpooled.copiedBuffer("X".getBytes(StandardCharsets.UTF_8)));

            assertFalse(embeddedChannel.isOpen(), "channel must be closed after a chunked framing error");
            verify(sessionListener).onSessionDown(eq(session), any(EOFException.class));
        } finally {
            embeddedChannel.close();
        }
    }

    /**
     * Any other error is passed on to the next handler and the session stays open.
     */
    @Test
    void testOtherErrorKeepsChannelOpen() {
        final var embeddedChannel = new EmbeddedChannel();
        try {
            addSession(embeddedChannel);

            final var error = new IllegalStateException("Malformed XML");
            embeddedChannel.pipeline().fireExceptionCaught(error);

            // EmbeddedChannel keeps an error that reached the end of the pipeline and throws it here
            assertSame(error, assertThrows(IllegalStateException.class, embeddedChannel::checkException));
            assertTrue(embeddedChannel.isOpen(), "channel must stay open after an error other than a framing error");
        } finally {
            embeddedChannel.close();
        }
    }

    /**
     * Add a session behind a {@link ChunkedFrameDecoder} to the pipeline of the channel.
     */
    private NetconfClientSession addSession(final EmbeddedChannel embeddedChannel) {
        final var session = new NetconfClientSession(sessionListener, embeddedChannel,
            new SessionIdType(Uint32.valueOf(20)), List.of());
        doNothing().when(sessionListener).onSessionUp(session);
        doNothing().when(sessionListener).onSessionDown(eq(session), any(EOFException.class));
        embeddedChannel.pipeline().addLast(new ChunkedFrameDecoder(4096), session);
        return session;
    }
}
