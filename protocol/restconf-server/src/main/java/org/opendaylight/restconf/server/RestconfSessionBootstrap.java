/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ConcurrentHTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
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
    protected Http2MultiplexHandler configureHttp2(final ChannelHandlerContext ctx) {
        ctx.pipeline().addBefore(null, null, Http2FrameCodecBuilder.forServer().build());
        return new Http2MultiplexHandler(new ConcurrentRestconfSession(scheme, ctx.channel().remoteAddress(), root));
    }
}
