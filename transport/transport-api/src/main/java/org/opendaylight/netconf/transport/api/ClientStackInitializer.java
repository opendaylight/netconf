/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Base class for initializing a single-channel {@link TransportStack}, i.e. like a TCP/UDP client.
 */
@Beta
// FIXME: 9.0.0: move to transport.spi
public final class ClientStackInitializer<
        S extends AbstractTransportStack<C>,
        C extends AbstractUnderlayTransportChannel> extends ChannelInboundHandlerAdapter {
    private final SettableFuture<S> stackFuture = SettableFuture.create();
    private final Function<Channel, @NonNull C> wrap;
    private final @NonNull S stack;

    @NonNullByDefault
    public ClientStackInitializer(final S stack, final Function<Channel, C> wrap) {
        this.stack = requireNonNull(stack);
        this.wrap = requireNonNull(wrap);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // Order of operations is important here: the stack should be visible before the underlying channel
        stackFuture.set(stack);
        final var channel = ctx.channel();
        stack.addTransportChannel(verifyNotNull(wrap.apply(channel)));

        // forward activation to any added handlers
        ctx.fireChannelActive();

        // remove this handler from the picture
        channel.pipeline().remove(this);
    }
}
