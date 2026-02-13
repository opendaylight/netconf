/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.common.Uint32;

class HTTPSchemePriorKnowledgeTest {
    @Test
    void http2PriorKnowledgeCleartext() {
        final var events = new ArrayList<>();
        final var eventCatcher = new SimpleChannelInboundHandler<>(false) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
                // ignore
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                events.add(evt);
            }
        };

        final var dummy = new ChannelInboundHandlerAdapter();
        final var channel = new EmbeddedChannel(dummy, eventCatcher);

        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(dummy), Uint32.valueOf(16384));

        final var preface = Unpooled.copiedBuffer("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n", CharsetUtil.US_ASCII);
        channel.writeInbound(preface);
        channel.runPendingTasks();

        assertTrue(events.contains(HTTPServerPipelineSetup.HTTP_2),
            "Expected HTTP/2 setup event for prior-knowledge preface");
        assertNotNull(channel.pipeline().get("h2-multiplexer"));
        channel.finishAndReleaseAll();
    }
}
