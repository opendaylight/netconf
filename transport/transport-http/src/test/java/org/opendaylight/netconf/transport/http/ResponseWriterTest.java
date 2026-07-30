/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.concurrent.EventExecutor;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResponseWriterTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private EventExecutor executor;

    // Stands in for Netty's own task queue: capture, but do not run, whatever scheduleDrain() submits, so the
    // test controls exactly when a scheduled drain actually executes.
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private ResponseWriter writer;

    @BeforeEach
    void setUp() {
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
        when(channel.isActive()).thenReturn(true);
        doAnswer(invocation -> {
            tasks.add(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(executor).execute(any());

        writer = new ResponseWriter();
    }

    /**
     * A drain triggered while already on the event loop must still be dispatched through the executor, not run
     * inline, so it cannot jump ahead of an earlier write still sitting in Netty's task queue.
     */
    @Test
    void writabilityChangeDoesNotDrainInline() throws Exception {
        when(channel.isWritable()).thenReturn(false);
        writer.handlerAdded(ctx);
        assertTrue(writer.sendResponsePart(Unpooled.EMPTY_BUFFER));

        when(channel.isWritable()).thenReturn(true);
        writer.channelWritabilityChanged(ctx);

        verify(ctx, never()).writeAndFlush(any());
        assertFalse(tasks.isEmpty());

        tasks.poll().run();
        verify(ctx).writeAndFlush(any(HttpObject.class));
    }
}
