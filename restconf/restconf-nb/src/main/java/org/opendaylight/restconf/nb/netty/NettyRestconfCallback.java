/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.netty;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponse;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * A {@link RestconfCallback} completing an {@link ChannelHandlerContext}.
 *
 * @param <V> value type
 */
abstract class NettyRestconfCallback<V> extends RestconfCallback<V> {
    private final ChannelHandlerContext ctx;

    NettyRestconfCallback(final ChannelHandlerContext ctx) {
        this.ctx = requireNonNull(ctx);
    }

    @Override
    public final void onSuccess(final V result) {
        final HttpResponse response;
        try {
            response = transform(result);
        } catch (RestconfDocumentedException e) {
            onFailure(e);
            return;
        }
        ctx.writeAndFlush(response);
    }

    @Override
    protected final void onFailure(final RestconfDocumentedException failure) {
        ctx.fireChannelRead(null);
    }

    abstract HttpResponse transform(V result) throws RestconfDocumentedException;
}
