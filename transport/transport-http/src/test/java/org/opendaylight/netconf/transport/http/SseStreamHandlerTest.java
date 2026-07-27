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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseStreamHandlerTest {
    @Mock
    private EventStreamListener listener;
    @Mock
    private EventStreamService.StartCallback callback;

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        channel = new EmbeddedChannel(new SseStreamHandler("/streams/test", callback, listener));
    }

    @AfterEach
    void afterEach() {
        channel.finishAndReleaseAll();
    }

    /**
     * An event larger than a QUIC packet arrives as several HttpContents, split in the middle of a line.
     */
    @Test
    void largeEventSplitIntoPacketSizedContents() {
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        final var value = "x".repeat(3000);
        final var bytes = ("event: big\r\ndata: " + value + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i += 1200) {
            channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(
                Arrays.copyOfRange(bytes, i, Math.min(i + 1200, bytes.length)))));
        }

        final var inOrder = inOrder(listener);
        inOrder.verify(listener).onEventField("event", "big");
        inOrder.verify(listener).onEventField("data", value);
    }

    @Test
    void tooLongLine() {
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        channel.writeInbound(new DefaultHttpContent(
            Unpooled.wrappedBuffer(new byte[HTTPServerLimits.DEFAULT.maxRequestBodySize() + 1])));

        assertFalse(channel.isOpen());
        verify(listener).onStreamEnd();
    }

    @Test
    void rejectedStream() {
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("not found", StandardCharsets.UTF_8)));

        final var cause = ArgumentCaptor.forClass(Throwable.class);
        verify(callback).onStartFailure(cause.capture());
        assertEquals("Status: 404 Not Found, Body: not found", cause.getValue().getMessage());
        verify(callback, never()).onStreamStarted(any());
        verifyNoMoreInteractions(callback);
    }
}
