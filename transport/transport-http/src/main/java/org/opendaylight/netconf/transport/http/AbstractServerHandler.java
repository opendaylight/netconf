/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;

/**
 * Abstract base class for end-of-pipeline {@link HTTPServer} handlers. It is exposed for testing purposes and hence it
 * is non-sealed. Were it not for tests, this class would be integrated directly into {@link HTTPServerSession}.
 */
@VisibleForTesting
abstract class AbstractServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    AbstractServerHandler() {
        super(FullHttpRequest.class, false);
    }

    @Override
    public final void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        // We may be involved in a cleartext upgrade to HTTP/2, in which case we need to route it outside the normal
        // read flow. Since we are restoring that magig, there is no need for downstream handlers to see this event.
        if (event instanceof HttpServerUpgradeHandler.UpgradeEvent upgrade) {
            final var request = upgrade.upgradeRequest();
            request.headers().setInt(ExtensionHeaderNames.STREAM_ID.text(), Http2CodecUtil.HTTP_UPGRADE_STREAM_ID);
            channelRead0(ctx, request.retain());
        } else {
            super.userEventTriggered(ctx, event);
        }
    }
}
