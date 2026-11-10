/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.util.ReferenceCountUtil.retain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerLimits;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PendingRequest;
import org.opendaylight.netconf.transport.http.PendingRequestListener;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class RestconfStreamingLifecycleTest {
    @Mock
    private EndpointRoot root;
    @Mock
    private PendingRequest<?> pending;

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void disconnectOrRemovalReleasesCompletedQueuedRequest(final boolean chunked, final boolean removeHandler)
            throws Exception {
        final var observed = new ArrayList<HttpRequest>();
        final AuthHandlerFactory auth = () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return "user";
            }

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {
                observed.add(request);
                ctx.fireChannelRead(retain(request));
            }
        };
        doReturn(auth).when(root).authHandlerFactory();
        doReturn(pending).when(root).prepare(any(TransportSession.class), eq(ImplementedMethod.POST),
            eq(URI.create("http://localhost/restconf/data")), any(HttpHeaders.class));
        final var started = new CompletableFuture<Boolean>();
        doAnswer(invocation -> {
            started.complete(true);
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), any(InputStream.class));

        final var channel = new EmbeddedChannel(new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(1024), HTTPServerLimits.DEFAULT, WriteBufferWaterMark.DEFAULT, new AltSvcAdvertiser("")));
        try {
            channel.writeInbound(Unpooled.copiedBuffer(
                "POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                    + "transfer-encoding: chunked\r\n\r\n0\r\n\r\n", CharsetUtil.US_ASCII));
            started.get(5, TimeUnit.SECONDS);
            final var queuedBytes = Unpooled.copiedBuffer(
                "POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                    + (chunked ? "transfer-encoding: chunked\r\n\r\n4\r\nbody\r\n0\r\n\r\n"
                        : "content-length: 4\r\n\r\nbody"), CharsetUtil.US_ASCII);
            channel.writeInbound(queuedBytes);
            assertEquals(2, observed.size());
            final var queued = assertInstanceOf(ReferenceCounted.class, observed.get(1));
            assertEquals(1, queued.refCnt());
            final var session = channel.pipeline().get(RestconfSession.class);
            if (removeHandler) {
                channel.pipeline().remove(session);
            } else {
                channel.close();
            }
            assertEquals(0, queued.refCnt());
            assertEquals(0, queuedBytes.refCnt());
            assertEquals(0, session.blockedRequestsSize());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @CsvSource({ "false,413", "true,413", "false,400", "true,400", "false,501", "true,501" })
    void rejectionWaitsForEarlierResponseToFlush(final boolean streamingFirst, final int status) throws Exception {
        final var listener = new CompletableFuture<PendingRequestListener>();
        doAnswer(invocation -> {
            listener.complete(invocation.getArgument(0));
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), nullable(InputStream.class));
        final var channel = newStreamingChannel();
        final var held = new HeldResponses();
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                + (streamingFirst ? "transfer-encoding: chunked\r\n\r\n0\r\n\r\n"
                    : "content-length: 0\r\n\r\n"), CharsetUtil.US_ASCII));
            final var executor = listener.get(5, TimeUnit.SECONDS);
            channel.pipeline().addBefore("http1-dispatcher", "held-responses", held);
            final var framing = switch (status) {
                case 400 -> "transfer-encoding: chunked, chunked\r\n\r\n";
                case 501 -> "transfer-encoding: gzip, chunked\r\n\r\n";
                default -> "transfer-encoding: chunked\r\n\r\n2\r\nab\r\n0\r\n\r\n";
            };
            channel.writeInbound(Unpooled.copiedBuffer("POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                + framing, CharsetUtil.US_ASCII));
            assertTrue(held.responses.isEmpty());
            assertNull(channel.readOutbound());
            assertTrue(channel.isActive());

            executor.requestComplete(pending, EmptyResponse.NO_CONTENT);
            channel.runPendingTasks();
            assertEquals(1, held.responses.size());
            assertEquals(204, held.responses.getFirst().response.status().code());
            // The prior response has been written but is not flushed: rejection must still wait.
            assertNull(channel.readOutbound());
            held.flushOne();
            channel.runPendingTasks();
            assertTrue(readWire(channel).startsWith("HTTP/1.1 204"));
            assertEquals(1, held.responses.size());
            assertEquals(status, held.responses.getFirst().response.status().code());
            held.flushOne();
            channel.runPendingTasks();
            assertTrue(readWire(channel).startsWith("HTTP/1.1 " + status));
            assertFalse(channel.isActive());
        } finally {
            held.releaseAll();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void earlierResponseIsNotAttributedToReceivingRequest() throws Exception {
        final var listener = new CompletableFuture<PendingRequestListener>();
        doAnswer(invocation -> {
            listener.complete(invocation.getArgument(0));
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), nullable(InputStream.class));
        final var channel = newStreamingChannel();
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                + "content-length: 0\r\n\r\n", CharsetUtil.US_ASCII));
            final var executor = listener.get(5, TimeUnit.SECONDS);
            channel.writeInbound(Unpooled.copiedBuffer("POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                + "transfer-encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));
            executor.requestComplete(pending, EmptyResponse.NO_CONTENT);
            channel.runPendingTasks();
            assertTrue(readWire(channel).startsWith("HTTP/1.1 204"));
            // This is the second request's body. The 204 above answered only the first request.
            channel.writeInbound(Unpooled.copiedBuffer("2\r\nab\r\n0\r\n\r\n", CharsetUtil.US_ASCII));
            assertTrue(readWire(channel).startsWith("HTTP/1.1 413"));
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void parserFailureDoesNotCloseBefore413Flushes() throws Exception {
        final var started = new CompletableFuture<Boolean>();
        final var responded = new CompletableFuture<Boolean>();
        doAnswer(invocation -> {
            final InputStream body = invocation.getArgument(1);
            final PendingRequestListener listener = invocation.getArgument(0);
            started.complete(true);
            try {
                body.read();
                responded.completeExceptionally(new AssertionError("Oversized content reached parser"));
            } catch (IOException e) {
                listener.requestFailed(pending, e);
                responded.complete(true);
            }
            return null;
        }).when(pending).execute(any(PendingRequestListener.class), any(InputStream.class));
        final var channel = newStreamingChannel();
        final var held = new HeldResponses();
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                + "transfer-encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));
            assertTrue(started.get(5, TimeUnit.SECONDS));
            channel.pipeline().addBefore("http1-dispatcher", "held-responses", held);
            channel.writeInbound(Unpooled.copiedBuffer("2\r\nab\r\n0\r\n\r\n", CharsetUtil.US_ASCII));
            assertTrue(responded.get(5, TimeUnit.SECONDS));
            channel.runPendingTasks();
            assertTrue(channel.isActive());
            assertNull(channel.readOutbound());
            assertEquals(1, held.responses.size());
            assertEquals(413, held.responses.getFirst().response.status().code());
            held.flushOne();
            channel.runPendingTasks();
            assertTrue(readWire(channel).startsWith("HTTP/1.1 413"));
            assertFalse(channel.isActive());
            assertTrue(held.responses.isEmpty());
        } finally {
            held.releaseAll();
            channel.finishAndReleaseAll();
        }
    }

    private EmbeddedChannel newStreamingChannel() {
        final AuthHandlerFactory auth = () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return "user";
            }

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {
                ctx.fireChannelRead(retain(request));
            }
        };
        doReturn(auth).when(root).authHandlerFactory();
        doReturn(pending).when(root).prepare(any(TransportSession.class), eq(ImplementedMethod.POST),
            eq(URI.create("http://localhost/restconf/data")), any(HttpHeaders.class));
        return new EmbeddedChannel(new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(1024), new HTTPServerLimits(8192, 16384, 8192, 1, HTTPServerLimits.MIN_FRAME_SIZE),
            WriteBufferWaterMark.DEFAULT, new AltSvcAdvertiser("")));
    }

    private static String readWire(final EmbeddedChannel channel) {
        final var result = new StringBuilder();
        ByteBuf bytes;
        while ((bytes = channel.readOutbound()) != null) {
            result.append(bytes.toString(CharsetUtil.US_ASCII));
            bytes.release();
        }
        return result.toString();
    }

    private static final class HeldResponses extends ChannelOutboundHandlerAdapter {
        private record Held(ChannelHandlerContext ctx, FullHttpResponse response, ChannelPromise promise) {
        }

        private final ArrayDeque<Held> responses = new ArrayDeque<>();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            responses.add(new Held(ctx, assertInstanceOf(FullHttpResponse.class, msg), promise));
        }

        void flushOne() {
            final var held = responses.removeFirst();
            held.ctx.writeAndFlush(held.response, held.promise);
        }

        void releaseAll() {
            Held held;
            while ((held = responses.pollFirst()) != null) {
                held.response.release();
                held.promise.tryFailure(new IOException("Test finished"));
            }
        }
    }

}
