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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
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
    void authenticationRejectionReleasesBodyWithoutAnotherResponse(final boolean forbidden) {
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
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1), auth);
        try {
            channel.writeInbound(request());
            final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
            assertEquals(forbidden ? 403 : 401, response.status().code());
            response.release();
            final var content = Unpooled.buffer().writeByte(42);
            channel.writeInbound(new DefaultHttpContent(content));
            assertEquals(0, content.refCnt());
            final var last = Unpooled.buffer().writeByte(43);
            channel.writeInbound(new DefaultLastHttpContent(last));
            assertEquals(0, last.refCnt());
            assertNull(channel.readOutbound());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void disconnectCancelsIncompleteBody() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1024));
        try {
            channel.writeInbound(request());
            final var request = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
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
            final var request = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overflowReleasesQueuedCrossingAndLaterContent(final boolean last) {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(request());
            final var streaming = (StreamingHttpRequest) channel.readInbound();
            try {
                final var queued = Unpooled.buffer().writeShort(42);
                channel.writeInbound(new DefaultHttpContent(queued));
                final var crossing = Unpooled.buffer().writeShort(43);
                // Deliver a decoder batch: later content can arrive before close has deregistered the pipeline.
                final var late = Unpooled.buffer().writeByte(44);
                channel.writeInbound(last ? new DefaultLastHttpContent(crossing) : new DefaultHttpContent(crossing),
                    new DefaultLastHttpContent(late));
                assertEquals(0, queued.refCnt());
                assertEquals(0, crossing.refCnt());
                assertThrows(IOException.class, streaming.body()::read);
                assertFalse(channel.isActive());
                final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
                assertEquals(413, response.status().code());
                response.release();
                assertEquals(0, late.refCnt());
                assertNull(channel.readInbound());
                assertNull(channel.readOutbound());
            } finally {
                streaming.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void exactLimitIncludesLastContentAndResetsForNextRequest() throws Exception {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            for (int i = 0; i < 2; ++i) {
                channel.writeInbound(request());
                final var streaming = (StreamingHttpRequest) channel.readInbound();
                try {
                    channel.writeInbound(new DefaultHttpContent(Unpooled.buffer().writeShort(42)));
                    channel.writeInbound(new DefaultLastHttpContent(Unpooled.buffer().writeByte(43)));
                    assertEquals(3, streaming.body().readAllBytes().length);
                    assertTrue(channel.isActive());
                    assertNull(channel.readOutbound());
                } finally {
                    streaming.release();
                }
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void overflowUnblocksRequestExecutorAfterConsumedBytes() throws Exception {
        final var consumed = new CompletableFuture<Boolean>();
        final var failed = new CompletableFuture<IOException>();
        doAnswer(invocation -> {
            final InputStream body = invocation.getArgument(1);
            consumed.complete(body.read() == 42 && Thread.currentThread().isVirtual());
            try {
                body.read();
                failed.completeExceptionally(new AssertionError("Overflow was delivered to the parser"));
            } catch (IOException e) {
                failed.complete(e);
            }
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), any(InputStream.class));
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1), session(pending));
        try {
            channel.writeInbound(request());
            channel.writeInbound(new DefaultHttpContent(Unpooled.buffer().writeByte(42)));
            assertTrue(consumed.get(5, TimeUnit.SECONDS));
            final var crossing = Unpooled.buffer().writeByte(43);
            channel.writeInbound(new DefaultLastHttpContent(crossing));
            assertInstanceOf(IOException.class, failed.get(5, TimeUnit.SECONDS));
            assertEquals(0, crossing.refCnt());
            final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
            assertEquals(413, response.status().code());
            response.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void chunkMetadataDoesNotCountTowardsLimit() throws Exception {
        final var channel = new EmbeddedChannel(new HttpServerCodec(), new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST / HTTP/1.1\r\nHost: localhost\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n1;extension=value\r\na\r\n2\r\nbc\r\n"
                + "0\r\nX-Trailer: value\r\n\r\n", CharsetUtil.US_ASCII));
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            try {
                assertEquals("abc", new String(streaming.body().readAllBytes(), CharsetUtil.US_ASCII));
                assertNull(channel.readOutbound());
            } finally {
                streaming.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Transfer-Encoding: chunked, chunked",
        "Transfer-Encoding: chunked, gzip",
        "Transfer-Encoding: gzip",
        "Transfer-Encoding: chunked,",
        "Transfer-Encoding: ,chunked",
        "Transfer-Encoding: chunked\r\nContent-Length: 1"
    })
    void invalidWireFramingNeverReachesParser(final String headers) {
        final var channel = new EmbeddedChannel(new HttpServerCodec(), new Http1RequestDispatcher(3),
            session(pending));
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST / HTTP/1.1\r\nHost: localhost\r\n"
                + headers + "\r\n\r\n", CharsetUtil.US_ASCII));
            assertNull(channel.readInbound());
            final var response = assertInstanceOf(ByteBuf.class, channel.readOutbound());
            try {
                assertTrue(response.toString(CharsetUtil.US_ASCII).startsWith("HTTP/1.1 400"));
            } finally {
                response.release();
            }
            verifyNoInteractions(pending);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void http10TransferEncodingIsRejected() {
        final var channel = new EmbeddedChannel(new HttpServerCodec(), new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST / HTTP/1.0\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));
            final var response = assertInstanceOf(ByteBuf.class, channel.readOutbound());
            try {
                assertTrue(response.toString(CharsetUtil.US_ASCII).startsWith("HTTP/1.1 400"));
            } finally {
                response.release();
            }
            assertNull(channel.readInbound());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void unsupportedTransferCodingNeverReachesParser() {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            final var request = request();
            request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "gzip, chunked");
            channel.writeInbound(request);
            assertNull(channel.readInbound());
            final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
            assertEquals(501, response.status().code());
            response.release();
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void malformedContentFailsBodyAndReleasesBuffers() {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(request());
            final var streaming = (StreamingHttpRequest) channel.readInbound();
            try {
                final var queued = Unpooled.buffer().writeByte(42);
                channel.writeInbound(new DefaultHttpContent(queued));
                final var malformed = new DefaultLastHttpContent(Unpooled.buffer().writeByte(43));
                malformed.setDecoderResult(DecoderResult.failure(new IOException("Malformed chunk")));
                channel.writeInbound(malformed);
                assertEquals(0, queued.refCnt());
                assertEquals(0, malformed.refCnt());
                assertThrows(IOException.class, streaming.body()::read);
                final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
                assertEquals(400, response.status().code());
                response.release();
                assertFalse(channel.isActive());
            } finally {
                streaming.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void earlySessionResponseIsNotFollowedBy413(final boolean missingHost) {
        final CompletedRequest completed = () -> EmptyResponse.NO_CONTENT;
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1), session(completed));
        try {
            final var request = request();
            if (missingHost) {
                request.headers().remove(HttpHeaderNames.HOST);
            }
            channel.writeInbound(request);
            final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
            assertEquals(missingHost ? 400 : 204, response.status().code());
            response.release();
            final var crossing = Unpooled.buffer().writeShort(42);
            channel.writeInbound(new DefaultLastHttpContent(crossing));
            assertEquals(0, crossing.refCnt());
            assertNull(channel.readOutbound());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closedConsumerWithoutResponseStillReceives413() {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(1));
        try {
            channel.writeInbound(request());
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            streaming.release();
            final var crossing = Unpooled.buffer().writeShort(42);
            channel.writeInbound(new DefaultLastHttpContent(crossing));
            assertEquals(0, crossing.refCnt());
            final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
            assertEquals(413, response.status().code());
            response.release();
            assertNull(channel.readOutbound());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedResponseWriteClosesOnlyForPendingRejection(final boolean reject) {
        final var failure = new IOException("Write failed");
        final var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                assertInstanceOf(FullHttpResponse.class, msg).release();
                promise.setFailure(failure);
            }
        }, new Http1RequestDispatcher(1));
        try {
            channel.writeInbound(request());
            assertInstanceOf(StreamingHttpRequest.class, channel.readInbound()).release();
            channel.writeInbound(new DefaultLastHttpContent());
            if (reject) {
                channel.writeInbound(request());
                assertInstanceOf(StreamingHttpRequest.class, channel.readInbound()).release();
                final var crossing = Unpooled.buffer().writeShort(42);
                channel.writeInbound(new DefaultLastHttpContent(crossing));
                assertEquals(0, crossing.refCnt());
            }
            final var promise = channel.newPromise();
            channel.pipeline().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NO_CONTENT), promise);
            channel.runPendingTasks();
            assertSame(failure, promise.cause());
            assertEquals(!reject, channel.isActive());
            assertNull(channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void failedAppendPreservesOriginalFailure() {
        final var channel = new EmbeddedChannel(new Http1RequestDispatcher(3));
        try {
            channel.writeInbound(request());
            final var streaming = assertInstanceOf(StreamingHttpRequest.class, channel.readInbound());
            try {
                final var original = new IOException("Parser stopped");
                streaming.body().fail(original);
                final var content = Unpooled.buffer().writeByte(42);
                channel.writeInbound(new DefaultHttpContent(content));
                assertEquals(0, content.refCnt());
                assertSame(original, assertThrows(IOException.class, streaming.body()::read).getCause());
                assertFalse(channel.isActive());
                assertNull(channel.readOutbound());
            } finally {
                streaming.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void executorFailureCannotWriteBehindBackpressured413() throws Exception {
        final var started = new CompletableFuture<Boolean>();
        final var responded = new CompletableFuture<Boolean>();
        doAnswer(invocation -> {
            final InputStream body = invocation.getArgument(1);
            final PendingRequestListener listener = invocation.getArgument(0);
            started.complete(true);
            try {
                body.read();
                responded.completeExceptionally(new AssertionError("Overflow reached parser"));
            } catch (IOException e) {
                listener.requestFailed(pending, e);
                responded.complete(true);
            }
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), any(InputStream.class));
        final var writes = new ArrayList<FullHttpResponse>();
        final var promises = new ArrayList<ChannelPromise>();
        final var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                // Hold transport writes and their promises, simulating a socket which cannot currently accept data.
                writes.add(assertInstanceOf(FullHttpResponse.class, msg));
                promises.add(promise);
            }
        }, new Http1RequestDispatcher(1), session(pending));
        try {
            channel.writeInbound(request());
            assertTrue(started.get(5, TimeUnit.SECONDS));
            channel.writeInbound(new DefaultLastHttpContent(Unpooled.buffer().writeShort(42)));
            assertTrue(responded.get(5, TimeUnit.SECONDS));
            channel.runPendingTasks();
            assertTrue(channel.isActive());
            assertEquals(1, writes.size());
            assertEquals(413, writes.getFirst().status().code());
            final var dropped = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.buffer().writeByte(42));
            final var droppedPromise = channel.newPromise();
            channel.pipeline().writeAndFlush(dropped, droppedPromise);
            channel.runPendingTasks();
            assertFalse(droppedPromise.isDone());
            assertEquals(0, dropped.refCnt());
            assertEquals(1, writes.size());
            promises.getFirst().setSuccess();
            channel.runPendingTasks();
            assertInstanceOf(ClosedChannelException.class, droppedPromise.cause());
            assertFalse(channel.isActive());
        } finally {
            writes.forEach(FullHttpResponse::release);
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
