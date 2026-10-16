/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opendaylight.yangtools.yang.common.Uint32;

@Timeout(10)
class ServerRequestExecutorTest {
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
}
