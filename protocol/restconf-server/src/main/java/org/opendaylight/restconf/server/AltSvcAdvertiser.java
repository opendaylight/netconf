/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Adds an {@value AltSvcAdvertiser#ALT_SVC} header to outbound {@link HttpResponse}s which do not already have this
 * header.
 */
@Sharable
final class AltSvcAdvertiser extends ChannelOutboundHandlerAdapter {
    private static final @NonNull String ALT_SVC = "Alt-Svc";

    @NonNullByDefault
    private final @NonNull CharSequence altSvcValue;

    AltSvcAdvertiser(final CharSequence altSvcValue) {
        this.altSvcValue = requireNonNull(altSvcValue);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof HttpResponse response) {
            final var headers = response.headers();
            if (!headers.contains(ALT_SVC)) {
                headers.set(ALT_SVC, altSvcValue);
            }
        }
        super.write(ctx, msg, promise);
    }
}
