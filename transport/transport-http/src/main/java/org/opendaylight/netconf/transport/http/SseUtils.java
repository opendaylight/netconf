/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/*
 * Collection of methods related to Server-Sent Event (SSE) support implementation.
 */
public final class SseUtils {
    static final String SSE_HANDLER_NAME = "sse-handler";
    static final String SSE_SERVICE_HANDLER_NAME = "sse-service";

    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");

    private SseUtils() {
        // hidden on purpose
    }

    /**
     * Enable SSE functionality on top of client's HTTP transport layer channel.
     *
     * @param channel client-side {@link HTTPTransportChannel}
     * @return an instance of {@link EventStreamService} to be used to request SSE stream using current connection.
     */
    public static EventStreamService enableClientSse(final HTTPTransportChannel channel) {
        final var nettyChannel = channel.channel();
        final var pipeline = nettyChannel.pipeline();

        final var http2Dispatcher = pipeline.get(ClientHttp2RequestDispatcher.class);
        final var http2AdapterContext = pipeline.context(HttpToHttp2ConnectionHandler.class);
        if (http2Dispatcher != null && http2AdapterContext != null) {
            // http 2
            final var sseService = new ClientHttp2SseService(channel, http2Dispatcher::nextStreamId);
            pipeline.addAfter(http2AdapterContext.name(), SSE_SERVICE_HANDLER_NAME, sseService);
            return sseService;
        }
        // http 1
        return new ClientHttp1SseService(nettyChannel);
    }

    /**
     * Builds binary representation of an SSE event taking into account the message length limits configured.
     *
     * @param fieldName the event field name
     * @param fieldValue the event field value
     * @param maxValueLength max message length, in chars
     * @param allocator the {@link ByteBufAllocator} instance used to allocate space for binary data
     * @return list of {@link ByteBuf}
     */
    public static List<ByteBuf> chunksOf(final String fieldName, final String fieldValue, final int maxValueLength,
            final ByteBufAllocator allocator) {
        final var valueStr = CRLF_MATCHER.removeFrom(requireNonNull(fieldValue));
        final var valueLen = valueStr.length();
        if (maxValueLength > 0 && valueLen > maxValueLength) {
            final var result = new ArrayList<ByteBuf>();
            for (int i = 0; i < valueLen; i += maxValueLength) {
                result.add(chunkOf(fieldName,
                    valueStr.substring(i, Math.min(i + maxValueLength, valueLen)), allocator));
            }
            return List.copyOf(result);
        }
        return List.of(chunkOf(fieldName, valueStr, allocator));
    }

    private static ByteBuf chunkOf(final String fieldName, final String fieldValue, final ByteBufAllocator allocator) {
        return ByteBufUtil.writeUtf8(allocator, requireNonNull(fieldName) + ": " + requireNonNull(fieldValue) + "\r\n");
    }

    /**
     * Parses SSE event binary representation and passes messages to listener provided.
     *
     * @param content SSE event binary data
     * @param listener a listener instance parsed  messages to be passed to
     */
    static void processChunks(final ByteBuf content, final EventStreamListener listener) {
        if (content.readableBytes() == 0) {
            return;
        }
        parseChunks(content).forEach(field -> {
            if (field.name().isEmpty()) {
                listener.onEventComment(field.value());
            } else {
                listener.onEventField(field.name(), field.value());
            }
        });
    }

    private static Stream<SseField> parseChunks(final ByteBuf content) {
        /*
            event         = *( comment / field ) end-of-line
            comment       = colon *any-char end-of-line
            field         = 1*name-char [ colon [ space ] *any-char ] end-of-line
            end-of-line   = ( cr lf / cr / lf )
         */
        return new String(ByteBufUtil.getBytes(content), StandardCharsets.UTF_8).lines()
            .map(line -> {
                final var length = line.length();
                final var maxIndex = length - 1;
                final var splitIndex = line.indexOf(':');

                if (splitIndex >= 0) {
                    final var name = splitIndex == 0 ? "" : line.substring(0, splitIndex);
                    final var valueIndex = line.charAt(splitIndex + 1) == ' ' ? splitIndex + 2 : splitIndex + 1;
                    final var value = valueIndex > maxIndex ? "" : line.substring(valueIndex);
                    return name.isEmpty() && value.isEmpty() ? null : new SseField(name, value);
                }

                // field name only, if non-empty
                return length > 0 ? new SseField(line, "") : null;
            })
            .filter(Objects::nonNull);
    }

    private record SseField(String name, String value) {
        SseField {
            requireNonNull(name);
            requireNonNull(value);
        }
    }
}
