/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Settings;

/**
 * A common pipeline step isolating downstreams from {@link Http2Settings} message.
 */
@Sharable
final class Http2SettingsFilter extends SimpleChannelInboundHandler<Http2Settings> {
    static final Http2SettingsFilter INSTANCE = new Http2SettingsFilter();

    private Http2SettingsFilter() {
        // Http2Settings is not refcounted
        super(Http2Settings.class, false);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Http2Settings msg) {
        // the HTTP 2 Settings message is expected once, just consume it then remove itself
        ctx.pipeline().remove(this);
    }
}
