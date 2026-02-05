/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Adds Alt-Svc advertisement to outbound HTTP responses.
 */
@Sharable
final class AltSvcAdvertiser extends ChannelOutboundHandlerAdapter {
    private static final AsciiString ALT_SVC = AsciiString.cached("Alt-Svc");

    private final @Nullable CharSequence altSvcValue;

    AltSvcAdvertiser(final @Nullable CharSequence altSvcValue) {
        this.altSvcValue = altSvcValue;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (altSvcValue != null && msg instanceof HttpResponse response
                && !response.headers().contains(ALT_SVC)) {
            response.headers().set(ALT_SVC, altSvcValue);
        }
        super.write(ctx, msg, promise);
    }
}
