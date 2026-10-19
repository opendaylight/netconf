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
import org.junit.jupiter.api.Test;

class HTTPSchemeCleartextUpgradeTest {
    // A h2c upgrade request carrying a body, so that HttpServerUpgradeHandler's aggregator ends up allocating
    // a composite buffer for it.
    private static final String UPGRADE_REQUEST = """
        POST /restconf/data HTTP/1.1\r
        Host: example.com\r
        Content-Type: text/plain\r
        Content-Length: 7\r
        Connection: Upgrade, HTTP2-Settings\r
        Upgrade: h2c\r
        HTTP2-Settings: AAMAAABkAAQAAP__\r
        \r
        payload""";

    @Test
    void h2cUpgradeReleasesUpgradeEvent() {
        final var dummy = new ChannelInboundHandlerAdapter();
        final var channel = new EmbeddedChannel(dummy);
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(dummy), HTTPServerLimits.DEFAULT);

        // Intercept the UpgradeEvent on its way to CleartextUpgradeHandler, acquiring a reference of our own. Note
        // Http2FrameCodec is installed between HttpServerUpgradeHandler and this handler during the upgrade.
        final var upgradeCtx = channel.pipeline().context(HttpServerUpgradeHandler.class);
        assertNotNull(upgradeCtx);
        final var events = new ArrayList<UpgradeEvent>();
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
        channel.releaseInbound();

        assertEquals(1, events.size());
        final var event = events.getFirst();
        assertEquals(1, event.refCnt());
        event.release();
        assertEquals(0, event.refCnt());

        channel.finishAndReleaseAll();
    }
}
