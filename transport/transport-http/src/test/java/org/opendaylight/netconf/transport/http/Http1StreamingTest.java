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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class Http1StreamingTest {
    @Mock
    private PendingRequest<?> pending;

    @Test
    void streamsBeforeLastContentThenAggregatesNextRequest() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024));
        try {
            final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpUtil.setTransferEncodingChunked(request, true);
            assertTrue(channel.writeInbound(request));
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            assertFalse(channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[]{42}))));
            assertEquals(42, streaming.body().read());
            assertFalse(channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[]{43}))));
            assertEquals(43, streaming.body().read());
            assertEquals(-1, streaming.body().read());
            streaming.release();

            final var next = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpUtil.setContentLength(next, 1);
            assertFalse(channel.writeInbound(next));
            assertTrue(channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[]{44}))));
            final var full = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
            assertEquals(44, full.content().readByte());
            full.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void bodySizeLimitResetsForEachStreamedRequest() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(2));
        try {
            for (int i = 0; i < 2; ++i) {
                assertTrue(channel.writeInbound(request()));
                final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
                try {
                    channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[]{42})));
                    channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[]{43})));
                    assertEquals(42, streaming.body().read());
                    assertEquals(43, streaming.body().read());
                    assertEquals(-1, streaming.body().read());
                    assertTrue(channel.isActive());
                } finally {
                    streaming.release();
                }
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exceedingBodySizeLimitFailsBodyAndReleasesContent(final boolean last) throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(request());
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            try {
                final var consumed = Unpooled.buffer().writeByte(42);
                channel.writeInbound(new DefaultHttpContent(consumed));
                assertEquals(42, streaming.body().read());
                assertEquals(0, consumed.refCnt());
                final var queued = Unpooled.buffer().writeByte(43);
                channel.writeInbound(new DefaultHttpContent(queued));
                final var oversized = Unpooled.wrappedBuffer(new byte[]{44, 45});
                channel.writeInbound(last ? new DefaultLastHttpContent(oversized) : new DefaultHttpContent(oversized));
                assertFalse(channel.isActive());
                assertEquals(0, queued.refCnt());
                assertEquals(0, oversized.refCnt());
                final var failure = assertThrows(IOException.class, streaming.body()::read);
                assertEquals("Request body exceeds maximum size 3", failure.getCause().getMessage());
            } finally {
                streaming.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sessionExecutesBeforeLastChunkAndClosesUnreadBody() throws Exception {
        final var started = new CompletableFuture<InputStream>();
        final var listener = new CompletableFuture<PendingRequestListener>();
        doAnswer(invocation -> {
            started.complete(invocation.getArgument(1));
            listener.complete(invocation.getArgument(0));
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), any(InputStream.class));
        final var session = session(pending);
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024), session);
        try {
            channel.writeInbound(request());
            final var body = started.get(5, TimeUnit.SECONDS);
            assertInstanceOf(RequestBodyInputStream.class, body);
            final var content = Unpooled.buffer().writeByte(42);
            channel.writeInbound(new DefaultHttpContent(content));
            assertEquals(42, body.read());
            final var unread = Unpooled.buffer().writeByte(43);
            channel.writeInbound(new DefaultHttpContent(unread));
            listener.get(5, TimeUnit.SECONDS).requestComplete(pending, EmptyResponse.NO_CONTENT);
            assertEquals(0, unread.refCnt());
            assertThrows(IOException.class, body::read);
            final var late = Unpooled.buffer().writeByte(44);
            channel.writeInbound(new DefaultLastHttpContent(late));
            assertEquals(0, late.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void completedRequestDiscardsRemainingBody() {
        final CompletedRequest completed = () -> EmptyResponse.NO_CONTENT;
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024), session(completed));
        try {
            channel.writeInbound(request());
            final var content = Unpooled.buffer().writeByte(42);
            channel.writeInbound(new DefaultLastHttpContent(content));
            assertEquals(0, content.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticationRejectionReleasesBody(final boolean forbidden) {
        final var auth = new AbstractBasicAuthHandler<String>() {
            @Override
            public String authenticate(final HttpRequest request) {
                return forbidden ? "user" : null;
            }

            @Override
            protected String authenticate(final String username, final String password) {
                return null;
            }

            @Override
            public boolean isAuthorized(final HttpRequest request, final String principal) {
                return false;
            }
        };
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024), auth);
        try {
            channel.writeInbound(request());
            final var response = (FullHttpResponse) channel.readOutbound();
            assertEquals(forbidden ? 403 : 401, response.status().code());
            response.release();
            final var content = Unpooled.buffer().writeByte(42);
            channel.writeInbound(new DefaultHttpContent(content));
            assertEquals(0, content.refCnt());
            final var last = Unpooled.buffer().writeByte(43);
            channel.writeInbound(new DefaultLastHttpContent(last));
            assertEquals(0, last.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void disconnectCancelsIncompleteBody() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024));
        try {
            channel.writeInbound(request());
            final var request = (StreamingHttpRequest) channel.readInbound();
            try {
                final var content = Unpooled.buffer().writeByte(42);
                channel.writeInbound(new DefaultHttpContent(content));
                channel.close();
                assertEquals(0, content.refCnt());
                assertThrows(IOException.class, request.body()::read);
            } finally {
                request.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void transportFailureClosesChannelAndReleasesBody() {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024));
        try {
            channel.writeInbound(request());
            final var request = (StreamingHttpRequest) channel.readInbound();
            try {
                final var content = Unpooled.buffer().writeByte(42);
                channel.writeInbound(new DefaultHttpContent(content));
                channel.pipeline().fireExceptionCaught(new IOException("transport failed"));
                assertFalse(channel.isActive());
                assertEquals(0, content.refCnt());
                assertThrows(IOException.class, request.body()::read);
                assertThrows(IOException.class, channel::checkException);
            } finally {
                request.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static HttpRequest request() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        request.headers().set(HttpHeaderNames.HOST, "localhost");
        HttpUtil.setTransferEncodingChunked(request, true);
        return request;
    }

    private static PipelinedHTTPServerSession session(final PreparedRequest prepared) {
        return new PipelinedHTTPServerSession(HTTPScheme.HTTP, Uint32.valueOf(1024)) {
            @Override
            protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI uri,
                    final HttpHeaders headers) {
                return prepared;
            }
        };
    }
}
