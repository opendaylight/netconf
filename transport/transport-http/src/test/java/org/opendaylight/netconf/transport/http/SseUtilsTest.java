/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseUtilsTest {

    @Mock
    private EventStreamListener eventStreamListener;

    @Test
    void chunksOf() {
        final var longMessage = "0123456789ABCDEF\r\n";
        // no split
        assertSameContent(
            List.of(byteBuf("field: 0123456789ABCDEF\r\n")),
            SseUtils.chunksOf("field", longMessage, 0, Unpooled.buffer().alloc()));
        // split
        assertSameContent(
            List.of(byteBuf("field: 0123456\r\n"), byteBuf("field: 789ABCD\r\n"), byteBuf("field: EF\r\n")),
            SseUtils.chunksOf("field", longMessage, 7, Unpooled.buffer().alloc()));
    }

    @Test
    void parseChunks() {
        final var content = byteBuf("field: value\r\nfield-name-only\r\n: comment-message\r\n: \r\nxy:  z\r\n");
        SseUtils.processChunks(content, eventStreamListener);

        final var inOrder = inOrder(eventStreamListener);
        inOrder.verify(eventStreamListener).onEventField("field", "value");
        inOrder.verify(eventStreamListener).onEventField("field-name-only", "");
        inOrder.verify(eventStreamListener).onEventComment("comment-message");
        // first space is ignored if exists, following space matches '*any-char' so it's a part of value
        inOrder.verify(eventStreamListener).onEventField("xy", " z");

        // empty expected to be omitted
        verify(eventStreamListener, never()).onEventField("","");
        verify(eventStreamListener, never()).onEventComment("");
    }

    private static ByteBuf byteBuf(final String content) {
        return Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertSameContent(final List<ByteBuf> expected, final List<ByteBuf> actual) {
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(ByteBufUtil.getBytes(expected.get(i)), ByteBufUtil.getBytes(actual.get(i)));
        }
    }
}
