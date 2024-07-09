/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.ServerChannelInitializer.REQUEST_DISPATCHER_HANDLER_NAME;

import com.google.common.base.CharMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AsciiString;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("RegexpSinglelineJava")
public final class SseUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SseUtils.class);

    static final String SSE_HANDLER_NAME = "sse-handler";
    static final AsciiString TEXT_EVENT_STREAM = AsciiString.of("text/event-stream");

    private static final char COLON = ':';
    private static final char SPACE = ' ';
    private static final String FIELD_CHUNK_TEMPLATE = "%s: %s\r\n";
    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");

    private SseUtils() {
        // hidden on purpose
    }

    public static void enableServerSse(final Channel channel, final EventStreamService service,
            final int maxFieldValueLength, final int heartbeatIntervalMillis) {
        final var sseHandler =
            new ServerSseHandler(requireNonNull(service), maxFieldValueLength, heartbeatIntervalMillis);
        requireNonNull(channel).pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                // setting SSE handler on first message arrival, not on initializer handler placement,
                // so the HTTP1 vs HTTP2 state expected to be established already,
                // it means the request passed upgrade codecs already and final codecs plus
                // optional auth handler and request dispatcher are in expected positions within pipeline
                ctx.pipeline().addBefore(REQUEST_DISPATCHER_HANDLER_NAME, SSE_HANDLER_NAME, sseHandler);
                // pass message to next handler
                ctx.fireChannelRead(msg);
                // remove this handler as no longer required
                ctx.pipeline().remove(this);
            }
        });
    }

    public static EventStreamService buildClientEventStreamService(final Channel channel) {
        System.out.println("client: " + channel.pipeline().toMap());
        System.out.println("names: " + channel.pipeline().names());
        return new ClientSseService(requireNonNull(channel));
    }

    static List<ByteBuf> chunksOf(final String fieldName, final String fieldValue, final int maxValueLength,
            final ByteBufAllocator allocator) {
        final var valueStr = CRLF_MATCHER.removeFrom(requireNonNull(fieldValue));
        final var valueLen = fieldValue.length();
        if (maxValueLength > 0 && valueLen > maxValueLength) {
            final var result = new LinkedList<ByteBuf>();
            for (int i = 0; i < valueLen; i += maxValueLength) {
                result.add(chunkOf(fieldName,
                    valueStr.substring(i, Math.min(i + maxValueLength, valueLen)), allocator));
            }
            return List.copyOf(result);
        }
        return List.of(chunkOf(fieldName, valueStr, allocator));
    }

    private static ByteBuf chunkOf(final String fieldName, final String fieldValue,
            final ByteBufAllocator allocator) {
        final var bytes = FIELD_CHUNK_TEMPLATE.formatted(requireNonNull(fieldName), requireNonNull(fieldValue))
            .getBytes(StandardCharsets.UTF_8);
        final var buf = allocator.buffer(bytes.length);
        buf.writeBytes(bytes);
        return buf;
    }

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

    private static List<SseField> parseChunks(final ByteBuf content) {
        /*
            event         = *( comment / field ) end-of-line
            comment       = colon *any-char end-of-line
            field         = 1*name-char [ colon [ space ] *any-char ] end-of-line
            end-of-line   = ( cr lf / cr / lf )
         */
        final var result = new LinkedList<SseField>();
        final String chunkStr = new String(ByteBufUtil.getBytes(content), StandardCharsets.UTF_8);
        chunkStr.lines().forEach(line -> {
            final var length = line.length();
            final var maxIndex = length - 1;
            final var splitIndex = line.indexOf(COLON);
            if (splitIndex >= 0) {
                final var name = splitIndex == 0 ? "" : line.substring(0, splitIndex);
                final var valueIndex = SPACE == (line.charAt(splitIndex + 1)) ? splitIndex + 2 : splitIndex + 1;
                final var value = valueIndex >= maxIndex ? "" : line.substring(valueIndex);
                if (!name.isEmpty() && !value.isEmpty()) {
                    result.add(new SseField(name, value));
                }
            } else if (length > 0) {
                // field name only
                result.add(new SseField(line, ""));
            }
        });
        System.out.println("parse result: " + result);
        return List.copyOf(result);
    }

    private record SseField(String name, String value) {
        SseField {
            requireNonNull(name);
            requireNonNull(value);
        }
    }
}
