/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * Cleartext HTTP/2 upgrade flow of {@link PlainHTTPClient}, exercised against Netty's own h2c server.
 */
@ExtendWith(MockitoExtension.class)
class PlainHTTPClientTest {
    @Mock
    private TransportChannelListener<HTTPTransportChannel> listener;

    /**
     * The server responds to the upgrade request on stream 1. That response has to be routed to the upgrade stream's
     * child channel, which requires {@link Http2MultiplexHandler} to be present before stream 1 is activated.
     * Otherwise, the response frame hits a {@code null} child channel and {@link NullPointerException} is thrown.
     */
    @Test
    void upgradeResponseIsRoutedToUpgradeStream() throws Exception {
        final var responder = new UpgradeStreamResponder();
        final var server = newServer(responder);
        final var clientChannel = new EmbeddedChannel(false, false);
        try {
            final var client = new PlainHTTPClient(listener, null, true);
            client.onUnderlayChannelEstablished(new TransportChannel() {
                @Override
                public Channel channel() {
                    return clientChannel;
                }
            });
            clientChannel.register();
            exchange(clientChannel, server);

            // the server has responded on stream 1, and that response has been delivered to the client
            assertEquals(1, responder.responses);

            // no exceptions should have reached the end of either pipeline
            clientChannel.checkException();
            server.checkException();

            // the transport should be up, with exactly one multiplexer in the pipeline
            verify(listener).onTransportChannelEstablished(any());
            verify(listener, never()).onTransportChannelFailed(any());
            assertEquals(1, clientChannel.pipeline().names().stream()
                .map(clientChannel.pipeline()::get)
                .filter(Http2MultiplexHandler.class::isInstance)
                .count());
        } finally {
            clientChannel.finishAndReleaseAll();
            server.finishAndReleaseAll();
        }
    }

    /**
     * A plain Netty h2c server, handing the upgrade stream to specified responder.
     */
    private static EmbeddedChannel newServer(final UpgradeStreamResponder responder) {
        // Http2MultiplexHandler considers itself server-side only if the channel's parent is a ServerChannel
        final var server = new EmbeddedChannel(new LocalServerChannel(), DefaultChannelId.newInstance(), true, false);
        final var serverCodec = new HttpServerCodec();
        server.pipeline().addLast(serverCodec, new HttpServerUpgradeHandler(serverCodec,
            protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
                ? new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer().build(),
                    new Http2MultiplexHandler(responder))
                : null));
        return server;
    }

    /**
     * Shuffle bytes between the two channels until neither has anything more to say.
     */
    private static void exchange(final EmbeddedChannel client, final EmbeddedChannel server) {
        boolean progress;
        do {
            progress = transfer(client, server) | transfer(server, client);
        } while (progress);
    }

    private static boolean transfer(final EmbeddedChannel from, final EmbeddedChannel to) {
        from.runPendingTasks();
        boolean transferred = false;
        for (ByteBuf buf; (buf = from.readOutbound()) != null; ) {
            to.writeInbound(buf);
            transferred = true;
        }
        to.runPendingTasks();
        return transferred;
    }

    /**
     * Server-side handler for the upgrade stream: responds to the upgrade request with 200 OK. It only counts
     * the responses it has sent, as any exception thrown here would end up in the stream's child channel, where
     * the test would not see it.
     */
    private static final class UpgradeStreamResponder extends ChannelInboundHandlerAdapter {
        int responses;

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            try {
                if (msg instanceof Http2HeadersFrame) {
                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200"), true));
                    responses++;
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
