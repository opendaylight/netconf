/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendaylight.yangtools.yang.common.Uint32;

@Timeout(10)
class ServerRequestExecutorTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rejectedAggregatedSubmissionReleasesTransferredReference(final boolean readable) {
        final var session = mock(PipelinedHTTPServerSession.class);
        final var ctx = mock(ChannelHandlerContext.class);
        final var pending = mock(PendingRequest.class);
        final var executor = new ServerRequestExecutor("rejected-aggregated", session, Uint32.valueOf(1024));
        executor.shutdown();
        final var buffer = Unpooled.buffer(1);
        try {
            if (readable) {
                buffer.writeByte(7);
            }
            // Keep one independent reference to verify that rejection releases exactly the transferred reference.
            buffer.retain();
            assertThrows(RejectedExecutionException.class,
                () -> executor.executeRequest(ctx, HttpVersion.HTTP_1_1, null, pending, buffer));
            assertEquals(1, buffer.refCnt());
            executor.requestComplete(pending, EmptyResponse.NO_CONTENT);
            verifyNoInteractions(session, ctx, pending);
        } finally {
            if (buffer.refCnt() != 0) {
                buffer.release(buffer.refCnt());
            }
        }
    }

    @Test
    void streamingSubmissionRequiresBody() {
        final var executor = new ServerRequestExecutor("missing-body", mock(PipelinedHTTPServerSession.class),
            Uint32.valueOf(1024));
        try {
            assertThrows(NullPointerException.class, () -> executor.executeStreamingRequest(
                mock(ChannelHandlerContext.class), HttpVersion.HTTP_1_1, null, mock(PendingRequest.class), null));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectedSubmissionDoesNotTrackRequestOrTakeBodyOwnership() throws Exception {
        final var session = mock(PipelinedHTTPServerSession.class);
        final var ctx = mock(ChannelHandlerContext.class);
        final var pending = mock(PendingRequest.class);
        final var executor = new ServerRequestExecutor("rejected-stream", session, Uint32.valueOf(1024));
        executor.shutdown();
        try (var stream = new RequestBodyInputStream()) {
            assertThrows(RejectedExecutionException.class,
                () -> executor.executeStreamingRequest(ctx, HttpVersion.HTTP_1_1, null, pending, stream));
            // A late completion must not find a mapping or send a response for a rejected submission.
            executor.requestComplete(pending, EmptyResponse.NO_CONTENT);
            verifyNoInteractions(session, ctx, pending);
            final var buffer = Unpooled.buffer(1).writeByte(7);
            try {
                assertTrue(stream.append(buffer));
                assertEquals(7, stream.read());
                assertEquals(1, buffer.refCnt());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void executesStreamingBodyOnRequestVirtualThread() throws Exception {
        final var executor = new ServerRequestExecutor("stream-test", mock(PipelinedHTTPServerSession.class),
            Uint32.valueOf(1024));
        try (var stream = new RequestBodyInputStream()) {
            final var started = new CompletableFuture<Thread>();
            final var firstRead = new CompletableFuture<Integer>();
            final var result = new CompletableFuture<byte[]>();
            final var pending = mock(PendingRequest.class);
            doAnswer(invocation -> {
                try (var body = invocation.<InputStream>getArgument(1)) {
                    assertSame(stream, body);
                    started.complete(Thread.currentThread());
                    firstRead.complete(body.read());
                    final var remaining = body.readAllBytes();
                    invocation.<PendingRequestListener>getArgument(0)
                        .requestComplete(pending, EmptyResponse.NO_CONTENT);
                    result.complete(remaining);
                } catch (IOException | AssertionError e) {
                    started.completeExceptionally(e);
                    firstRead.completeExceptionally(e);
                    result.completeExceptionally(e);
                }
                return null;
            }).when(pending).execute(any(), any());

            executor.executeStreamingRequest(mock(ChannelHandlerContext.class), HttpVersion.HTTP_1_1, null, pending,
                stream);
            final var reader = started.get(5, TimeUnit.SECONDS);
            assertTrue(reader.isVirtual());
            assertTrue(reader.getName().startsWith("stream-test-http-server-req-"));
            assertFalse(firstRead.isDone());
            final var first = Unpooled.wrappedBuffer(new byte[] { 1 });
            try {
                stream.append(first);
            } finally {
                first.release();
            }
            assertEquals(1, firstRead.get(5, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            final var last = Unpooled.wrappedBuffer(new byte[] { 2, 3 });
            try {
                stream.append(last);
            } finally {
                last.release();
            }
            stream.complete();
            assertArrayEquals(new byte[] { 2, 3 }, result.get(5, TimeUnit.SECONDS));
            assertEquals(0, first.refCnt());
            assertEquals(0, last.refCnt());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shutdownCancelsBlockedReaderAndReleasesOtherTrackedBodies() throws Exception {
        final var executor = new ServerRequestExecutor("shutdown-stream", mock(PipelinedHTTPServerSession.class),
            Uint32.valueOf(1024));
        try (var blocked = new RequestBodyInputStream(); var queued = new RequestBodyInputStream()) {
            final var started = new CompletableFuture<Thread>();
            final var result = new CompletableFuture<IOException>();
            final var pending = mock(PendingRequest.class);
            doAnswer(invocation -> {
                started.complete(Thread.currentThread());
                try {
                    invocation.<InputStream>getArgument(1).read();
                    result.completeExceptionally(new AssertionError("Read should have failed"));
                } catch (IOException e) {
                    result.complete(e);
                }
                return null;
            }).when(pending).execute(any(), any());
            final var ctx = mock(ChannelHandlerContext.class);
            executor.executeStreamingRequest(ctx, HttpVersion.HTTP_1_1, null, pending, blocked);
            final var reader = started.get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).until(() -> reader.getState() == Thread.State.WAITING);

            final var buffer = Unpooled.buffer(1).writeByte(7);
            try {
                queued.append(buffer);
            } finally {
                buffer.release();
            }
            queued.complete();
            executor.executeStreamingRequest(ctx, HttpVersion.HTTP_1_1, null, mock(PendingRequest.class), queued);

            executor.shutdown();
            assertInstanceOf(CancellationException.class, result.get(5, TimeUnit.SECONDS).getCause());
            assertEquals(0, buffer.refCnt());
            assertInstanceOf(CancellationException.class, assertThrows(IOException.class, queued::read).getCause());
            executor.shutdown();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Verifies that request completion and failure release unread streaming buffers before sending the response,
     * whether or not the upload has completed. Further content is discarded until producer completion, then rejected.
     *
     * <table>
     *   <caption>Test scenarios</caption>
     *   <tr><th>failed</th><th>completed</th><th>Scenario</th></tr>
     *   <tr><td>false</td><td>false</td><td>Request succeeds while the upload is ongoing</td></tr>
     *   <tr><td>false</td><td>true</td><td>Request succeeds after the upload completes</td></tr>
     *   <tr><td>true</td><td>false</td><td>Request fails while the upload is ongoing</td></tr>
     *   <tr><td>true</td><td>true</td><td>Request fails after the upload completes</td></tr>
     * </table>
     */
    @ParameterizedTest
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    void requestTerminationClosesUnreadStreamingBody(final boolean failed, final boolean completed) throws Exception {
        final var session = mock(PipelinedHTTPServerSession.class);
        final var ctx = mock(ChannelHandlerContext.class);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        final var pending = mock(PendingRequest.class);
        final var executor = new ServerRequestExecutor("early-termination", session, Uint32.valueOf(1024));
        try (var stream = new RequestBodyInputStream()) {
            final var partial = Unpooled.wrappedBuffer(new byte[] { 1, 2 });
            final var queued = Unpooled.wrappedBuffer(new byte[] { 3, 4 });
            try {
                stream.append(partial);
                stream.append(queued);
            } finally {
                partial.release();
                queued.release();
            }
            if (completed) {
                stream.complete();
            }
            final var responded = new CompletableFuture<Void>();
            doAnswer(invocation -> {
                try {
                    // Cleanup must happen before handing the response to the transport.
                    assertEquals(0, partial.refCnt());
                    assertEquals(0, queued.refCnt());
                    assertThrows(IOException.class, stream::read);
                    responded.complete(null);
                } catch (AssertionError e) {
                    responded.completeExceptionally(e);
                } finally {
                    ReferenceCountUtil.release(invocation.getArgument(2));
                }
                return null;
            }).when(session).respond(any(), any(), any());
            doAnswer(invocation -> {
                try {
                    final var body = invocation.<InputStream>getArgument(1);
                    assertSame(stream, body);
                    assertEquals(1, body.read());
                    // Deliberately leave the body open, as a parser stopping early may do.
                    final var listener = invocation.<PendingRequestListener>getArgument(0);
                    if (failed) {
                        listener.requestFailed(pending, new IOException("Malformed body"));
                    } else {
                        listener.requestComplete(pending, EmptyResponse.NO_CONTENT);
                    }
                } catch (IOException | AssertionError e) {
                    responded.completeExceptionally(e);
                }
                return null;
            }).when(pending).execute(any(), any());

            executor.executeStreamingRequest(ctx, HttpVersion.HTTP_1_1, null, pending, stream);
            responded.get(5, TimeUnit.SECONDS);
            final var late = Unpooled.buffer(1).writeByte(5);
            try {
                if (completed) {
                    assertThrows(IllegalStateException.class, () -> stream.append(late));
                } else {
                    assertFalse(stream.append(late));
                    stream.complete();
                    assertThrows(IllegalStateException.class, () -> stream.append(late));
                }
                assertEquals(1, late.refCnt());
            } finally {
                late.release();
            }
        } finally {
            executor.shutdown();
        }
    }
}
