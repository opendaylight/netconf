/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HTTPSchemeCleartextUpgradeTest {
    private static final String UPGRADE_REQUEST = """
        GET /restconf/data HTTP/1.1\r
        Host: example.com\r
        Connection: Upgrade, HTTP2-Settings\r
        Upgrade: h2c\r
        HTTP2-Settings: AAMAAABkAAQAAP__\r
        \r
        """;

    private final List<UpgradeEvent> events = new ArrayList<>();

    private EmbeddedChannel channel;

    @AfterEach
    void afterEach() {
        events.forEach(UpgradeEvent::release);
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void h2cUpgradeReleasesUpgradeEvent() {
        final var handler = new ChannelInboundHandlerAdapter();
        channel = new EmbeddedChannel(handler);
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(handler), HTTPServerLimits.DEFAULT);

        // Intercept the UpgradeEvent on its way to CleartextUpgradeHandler, acquiring a reference of our own.
        final var upgradeCtx = channel.pipeline().context(HttpServerUpgradeHandler.class);
        assertNotNull(upgradeCtx);
        channel.pipeline().addAfter(upgradeCtx.name(), null, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                if (evt instanceof UpgradeEvent event) {
                    events.add(event.retain());
                }
                ctx.fireUserEventTriggered(evt);
            }
        });

        channel.writeInbound(Unpooled.copiedBuffer(UPGRADE_REQUEST, CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals(1, events.size());
        // Only our own reference should remain, anything more means the pipeline leaked the UpgradeEvent
        assertEquals(1, events.getFirst().refCnt(), "UpgradeEvent was not released by CleartextUpgradeHandler");
    }
}
