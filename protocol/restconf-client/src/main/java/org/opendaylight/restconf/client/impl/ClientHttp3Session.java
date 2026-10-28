/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static org.opendaylight.netconf.transport.http.HTTPClient.getAuthFactory;
import static org.opendaylight.netconf.transport.http.HTTPTransportStack.MAX_HTTP_CONTENT_LENGTH;

import com.google.common.base.VerifyException;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side {@link ClientSession} implementation for HTTP/3.
 *
 * <p>Serves as a gateway to the underlying Netty {@link QuicChannel}. It spawns a new QUIC request stream for
 * every outbound RESTCONF request, mapping the asynchronous responses back to the caller. The
 * {@link Http3FrameToHttpObjectCodec} translates between HTTP/3 frames and HTTP objects. Once the request has been
 * written, it half-closes the stream by shutting down its output, while the input stays open to read the response.
 * It also always sets the request's {@code :scheme} to {@code https}, as RFC 9114 section 3.1 mandates, so unlike
 * {@link ClientHttp2Session} this class does not set it.
 */
public final class ClientHttp3Session extends ClientSession {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp3Session.class);

    private static @NonNull QuicChannel verifyChannel(final Channel channel) {
        if (channel instanceof QuicChannel quic) {
            return quic;
        }
        throw new VerifyException("Unexpected channel " + channel);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        verifyChannel(ctx.channel());
        super.handlerAdded(ctx);
    }

    @Override
    protected void executeRequest(final Channel channel, final FullHttpRequest request,
            final FutureCallback<FullHttpResponse> callback) {
        Http3.newRequestStream(verifyChannel(channel), new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel ch) {
                final var pipeline = ch.pipeline()
                    .addLast(new Http3FrameToHttpObjectCodec(false))
                    .addLast(new HttpContentDecompressor(0));

                // Generate and attach Auth handler if available
                final var authFactory = getAuthFactory(channel);
                if (authFactory != null) {
                    final var authProvider = authFactory.get();
                    if (authProvider != null) {
                        pipeline.addLast(authProvider);
                    }
                }

                pipeline
                    .addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH))
                    .addLast(new SingleResponseHandler(callback));
            }
        }).addListener((FutureListener<QuicStreamChannel>) sf -> {
            final var sfCause = sf.cause();
            if (sfCause != null) {
                // the cause is reported through the callback, log at debug level only to not report it twice
                LOG.debug("Failed to open HTTP/3 stream for request {}", request.uri(), sfCause);
                // Prevent memory leak if stream creation fails
                request.release();
                callback.onFailure(sfCause);
                return;
            }

            sf.getNow().writeAndFlush(request).addListener((ChannelFutureListener) wf -> {
                final var wfCause = wf.cause();
                if (wfCause != null) {
                    final var ch = (QuicStreamChannel) wf.channel();
                    LOG.error("Failed to write request to HTTP/3 stream {}", ch.streamId(), wfCause);
                    ch.pipeline().fireExceptionCaught(wfCause);
                }
            });
        });
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        // No-op: this handler sits on the parent QuicChannel's pipeline, which never receives a FullHttpResponse
        // directly. Each request's response is instead read by the SingleResponseHandler added in initChannel()
        // above, on that request's own per-stream QuicStreamChannel pipeline.
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        clearChannel();
        super.channelInactive(ctx);
    }
}
