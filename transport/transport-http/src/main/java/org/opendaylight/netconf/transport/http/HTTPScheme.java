/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Supported HTTP schemes.
 */
public enum HTTPScheme {
    HTTP(HttpScheme.HTTP) {
        private static final UpgradeCodecFactory UCF = protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                return new Http2ServerUpgradeCodec(new HelloWorldHttp2HandlerBuilder().build());
            } else {
                return null;
            }
        };

        @Override
        void initializeServer(final ChannelPipeline pipeline, final HTTPServerSessionFactory factory) {
          // pretty much lifted from Netty's
          // example/src/main/java/io/netty/example/http2/helloworld/server/Http2ServerInitializer.java#L81-L110

          final HttpServerCodec sourceCodec = new HttpServerCodec();
          final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, UCF);
          final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                  new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler,
                                                         new Http2ServerUpgradeCodecBuilder().build());

          p.addLast(cleartextHttp2ServerUpgradeHandler);

          // Integrated into
          p.addLast(new SimpleChannelInboundHandler<HttpMessage>() {
              @Override
              protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage msg) throws Exception {
                  // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                  System.err.println("Directly talking: " + msg.protocolVersion() + " (no upgrade was attempted)");
                  ChannelPipeline pipeline = ctx.pipeline();
                  pipeline.addAfter(ctx.name(), null, new HelloWorldHttp1Handler("Direct. No Upgrade Attempted."));
                  pipeline.replace(this, null, new HttpObjectAggregator(maxHttpContentLength));
                  ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
              }
          });

        }
    },
    HTTPS(HttpScheme.HTTPS) {
        @Override
        void initializeServer(final ChannelPipeline pipeline, final HTTPServerSessionFactory factory) {
            // FIXME: ServerSessionNegotiator
//            pipeline.addLast(new Http2OrHttpHandler());
        }
    };

    private final @NonNull HttpScheme netty;

    HTTPScheme(final @Nullable HttpScheme netty) {
        this.netty = requireNonNull(netty);
    }

    public final @NonNull HttpScheme netty() {
        return netty;
    }

    @Override
    public String toString() {
        return netty.toString();
    }

    @NonNullByDefault
    // FIXME: ServerSessionFactory
    abstract void initializeServer(ChannelPipeline pipeline, HTTPServerSessionFactory factory);
}
