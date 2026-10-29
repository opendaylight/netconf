/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

class Http1StreamingIntegrationTest {
    @Test
    void streamsBeforeLastContentThenAggregatesNextRequest() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024));
        try {
            final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpUtil.setTransferEncodingChunked(request, true);
            assertTrue(channel.writeInbound(request));
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            assertFalse(channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[] { 42 }))));
            assertEquals(42, streaming.body().read());
            assertFalse(channel.writeInbound(new DefaultLastHttpContent(
                Unpooled.wrappedBuffer(new byte[] { 43 }))));
            assertEquals(43, streaming.body().read());
            assertEquals(-1, streaming.body().read());
            streaming.body().close();

            final var next = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpUtil.setContentLength(next, 1);
            assertFalse(channel.writeInbound(next));
            assertTrue(channel.writeInbound(new DefaultLastHttpContent(
                Unpooled.wrappedBuffer(new byte[] { 44 }))));
            final var full = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
            assertEquals(44, full.content().readByte());
            full.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
