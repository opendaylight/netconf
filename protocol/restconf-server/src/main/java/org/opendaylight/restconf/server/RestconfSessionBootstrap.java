/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ConcurrentHTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.Http2Hooks;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfSessionBootstrap.class);

    private final EndpointRoot root;

    RestconfSessionBootstrap(final HTTPScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
        return new RestconfSession(scheme, ctx.channel().remoteAddress(), root);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        // Make the child initializer available ASAP (before transport sets up upgrade/ALPN)
        ctx.channel().attr(Http2Hooks.CHILD_INIT).set(buildChildInit(ctx));
        // Now let transport install its HTTP/1.1 / upgrade / ALPN handlers
        super.handlerAdded(ctx);
    }

    private ChannelInitializer<Channel> buildChildInit(ChannelHandlerContext parentCtx) {
        return new ChannelInitializer<>() {
            @Override protected void initChannel(Channel ch) {
                final var pipeline = ch.pipeline();
                pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(16 * 1024 * 1024));
                pipeline.addLast(root.authHandlerFactory().create());
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(scheme,
                    parentCtx.channel().remoteAddress(), root));
            }
        };
    }

    @Override
    protected ConcurrentHTTPServerSession configureHttp2(final ChannelHandlerContext ctx) {
        return new ConcurrentRestconfSession(scheme, ctx.channel().remoteAddress(), root);
    }
}
