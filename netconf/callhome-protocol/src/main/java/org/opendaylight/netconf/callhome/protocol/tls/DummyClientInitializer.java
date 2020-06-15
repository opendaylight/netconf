/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyClientInitializer.class);

    private final SslConfigurationProvider sslConfigurationProvider;

    public DummyClientInitializer(SslConfigurationProvider sslConfigurationProvider) {
        this.sslConfigurationProvider = sslConfigurationProvider;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
        pipeline.addLast("length-decoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        pipeline.addLast("String-decoder", new StringDecoder());
        pipeline.addLast("length-encoder", new LengthFieldPrepender(4));
        pipeline.addLast("String-encoder", new StringEncoder());
        pipeline.addFirst(sslConfigurationProvider.getSslHandler());
        pipeline.fireChannelActive();
        LOG.error("Channel has been initialized");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOG.error("Channel is active");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.error("Channel is active");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        LOG.error("Reading from channel...");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (!((SslHandshakeCompletionEvent) evt).isSuccess()) {
                LOG.error("Handshake failed");
            }
        }
    }
}
