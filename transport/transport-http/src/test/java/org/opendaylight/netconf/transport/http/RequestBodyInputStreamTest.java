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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class RequestBodyInputStreamTest {
    @Test
    void bulkReadsPreserveBytesDuringConcurrentDelivery() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var expected = new byte[16384];
            for (int i = 0; i < expected.length; ++i) {
                expected[i] = (byte) i;
            }
            final var result = new CompletableFuture<byte[]>();
            final var reader = Thread.ofVirtual().start(() -> {
                try {
                    final var actual = new byte[expected.length];
                    int offset = 0;
                    while (offset < actual.length) {
                        final int count = stream.read(actual, offset, Math.min(97, actual.length - offset));
                        assertTrue(count > 0);
                        offset += count;
                    }
                    assertEquals(-1, stream.read());
                    result.complete(actual);
                } catch (IOException | AssertionError e) {
                    result.completeExceptionally(e);
                }
            });
            final var buffers = new ArrayList<ByteBuf>();
            try {
                awaitWaiting(reader);
                for (int offset = 0; offset < expected.length; offset += 64) {
                    final var buffer = Unpooled.wrappedBuffer(expected, offset, 64);
                    buffers.add(buffer);
                    try {
                        assertTrue(stream.append(buffer));
                    } finally {
                        buffer.release();
                    }
                }
                stream.complete();
                assertArrayEquals(expected, result.get(5, TimeUnit.SECONDS));
                buffers.forEach(buffer -> assertEquals(0, buffer.refCnt()));
            } finally {
                stream.close();
                reader.join(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void earlyConsumerCloseRejectsContentWithoutRetainingIt() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var queued = append(stream, (byte) 1, (byte) 2);
            assertEquals(1, stream.read());
            stream.close();
            stream.close();
            assertEquals(0, queued.refCnt());
            assertThrows(IOException.class, stream::read);
            final var late = Unpooled.buffer(1).writeByte(3);
            try {
                assertFalse(stream.append(late));
                stream.fail(new IOException("Transport failed after consumer close"));
                assertEquals("Request body closed", assertThrows(IOException.class, stream::read).getMessage());
                assertFalse(stream.append(late));
                assertFalse(stream.append(Unpooled.EMPTY_BUFFER));
                assertEquals(1, late.refCnt());
                assertEquals(0, late.readerIndex());
            } finally {
                late.release();
            }
            // Producer completion still establishes a boundary even when the consumer finished early.
            stream.complete();
            assertRejected(stream);
        }
    }

    @Test
    void readsAcrossBuffersAndReleasesConsumedSlices() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var first = Unpooled.wrappedBuffer(new byte[] { 9, (byte) 255, 1, 2 });
            first.skipBytes(1);
            stream.append(first);
            assertEquals(1, first.readerIndex());
            assertEquals(2, first.refCnt());
            first.release();
            final var second = append(stream, (byte) 3, (byte) 4);
            stream.complete();

            assertEquals(255, stream.read());
            final var bytes = new byte[5];
            assertEquals(3, stream.read(bytes, 1, 3));
            assertArrayEquals(new byte[] { 0, 1, 2, 3, 0 }, bytes);
            assertEquals(0, first.refCnt());
            assertEquals(1, second.refCnt());
            assertEquals(1, stream.read(bytes));
            assertEquals(4, bytes[0]);
            assertEquals(0, second.refCnt());
            assertEquals(-1, stream.read());
            assertEquals(-1, stream.read(bytes));
            assertEquals(-1, stream.read());
        }
    }

    @Test
    void validatesReadsAndHandlesEmptyContent() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var empty = Unpooled.buffer(1);
            try {
                stream.append(empty);
                assertEquals(1, empty.refCnt());
            } finally {
                empty.release();
            }
            assertEquals(0, stream.read(new byte[1], 1, 0));
            assertThrows(NullPointerException.class, () -> stream.read(null, 0, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 0, 2));
            stream.complete();
            assertEquals(-1, stream.read());
            assertEquals(0, stream.read(new byte[0]));
            stream.close();
            assertEquals(0, stream.read(new byte[0]));
        }
    }

    @Test
    void readerWaitsForEachChunkAndReturnsShortReads() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var firstRead = new CompletableFuture<Integer>();
            final var allRead = new CompletableFuture<byte[]>();
            final var reader = Thread.ofVirtual().start(() -> {
                try {
                    final var bytes = new byte[4];
                    firstRead.complete(stream.read(bytes));
                    final int count = stream.read(bytes, 1, 3);
                    assertEquals(2, count);
                    assertEquals(-1, stream.read());
                    allRead.complete(bytes);
                } catch (IOException | AssertionError e) {
                    firstRead.completeExceptionally(e);
                    allRead.completeExceptionally(e);
                }
            });
            try {
                awaitWaiting(reader);
                assertFalse(firstRead.isDone());
                append(stream, (byte) 1);
                assertEquals(1, firstRead.get(5, TimeUnit.SECONDS));
                awaitWaiting(reader);
                append(stream, (byte) 2, (byte) 3);
                stream.complete();
                assertArrayEquals(new byte[] { 1, 2, 3, 0 }, allRead.get(5, TimeUnit.SECONDS));
            } finally {
                stream.close();
                reader.join(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void failureDiscardsPartialAndQueuedBuffersAndPreservesCause() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var first = append(stream, (byte) 1, (byte) 2);
            final var second = append(stream, (byte) 3);
            assertEquals(1, stream.read());
            final var cause = new IllegalStateException("transport failed");
            stream.fail(cause);
            assertEquals(0, first.refCnt());
            assertEquals(0, second.refCnt());
            final var failure = assertThrows(IOException.class, stream::read);
            assertSame(cause, failure.getCause());
            stream.fail(new IOException("later failure"));
            stream.complete();
            stream.cancel();
            stream.close();
            failure.addSuppressed(new IOException("caller-specific cleanup"));
            final var nextFailure = assertThrows(IOException.class, () -> stream.read(new byte[1]));
            assertNotSame(failure, nextFailure);
            assertSame(cause, nextFailure.getCause());
            assertEquals(0, nextFailure.getSuppressed().length);
            assertRejected(stream);
        }
    }

    @Test
    void completionRejectsMoreContentButCloseDiscardsUnreadBytes() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var buffer = append(stream, (byte) 1, (byte) 2);
            stream.complete();
            stream.complete();
            stream.fail(new IOException("late failure"));
            assertRejected(stream);
            assertEquals(1, stream.read());
            stream.close();
            stream.close();
            assertEquals(0, buffer.refCnt());
            assertThrows(IOException.class, stream::read);
            assertRejected(stream);
        }
    }

    @Test
    void cancellationAfterCompletionDiscardsUnreadContent() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var buffer = append(stream, (byte) 1, (byte) 2);
            stream.complete();
            assertEquals(1, stream.read());
            stream.cancel();
            assertEquals(0, buffer.refCnt());
            final var failure = assertThrows(IOException.class, stream::read);
            assertInstanceOf(CancellationException.class, failure.getCause());
            stream.close();
            stream.complete();
            assertSame(failure.getCause(), assertThrows(IOException.class, stream::read).getCause());
            assertRejected(stream);
        }
    }

    @Test
    void cancellationDiscardsQueuedContent() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var buffer = append(stream, (byte) 1);
            stream.cancel();
            stream.cancel();
            assertEquals(0, buffer.refCnt());
            assertInstanceOf(CancellationException.class, assertThrows(IOException.class, stream::read).getCause());
            assertRejected(stream);
        }
    }

    @Test
    void terminalSignalsWakeWaitingReader() throws Exception {
        assertWakes(RequestBodyInputStream::complete, null);
        assertWakes(stream -> stream.fail(new IllegalStateException()), IOException.class);
        assertWakes(RequestBodyInputStream::cancel, IOException.class);
        assertWakes(RequestBodyInputStream::close, IOException.class);
    }

    @Test
    void interruptionAbortsStreamAndRestoresInterruptStatus() throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var result = new CompletableFuture<IOException>();
            final var interrupted = new CompletableFuture<Boolean>();
            final var reader = Thread.ofVirtual().start(() -> {
                try {
                    stream.read();
                    result.completeExceptionally(new AssertionError("Read should have failed"));
                } catch (IOException e) {
                    result.complete(e);
                    interrupted.complete(Thread.currentThread().isInterrupted());
                }
            });
            try {
                awaitWaiting(reader);
                reader.interrupt();
                final var initialFailure = result.get(5, TimeUnit.SECONDS);
                assertInstanceOf(InterruptedIOException.class, initialFailure);
                final var nextFailure = assertThrows(InterruptedIOException.class, stream::read);
                assertNotSame(initialFailure, nextFailure);
                assertSame(initialFailure.getCause(), nextFailure.getCause());
                assertTrue(interrupted.get(5, TimeUnit.SECONDS));
                assertRejected(stream);
            } finally {
                stream.close();
                reader.join(Duration.ofSeconds(5));
            }
        }
    }

    private static void assertWakes(final Consumer<RequestBodyInputStream> terminate,
            final Class<? extends IOException> expected) throws Exception {
        try (var stream = new RequestBodyInputStream()) {
            final var result = new CompletableFuture<Object>();
            final var reader = Thread.ofVirtual().start(() -> {
                try {
                    result.complete(stream.read(new byte[8]));
                } catch (IOException e) {
                    result.complete(e);
                }
            });
            try {
                awaitWaiting(reader);
                terminate.accept(stream);
                final var value = result.get(5, TimeUnit.SECONDS);
                if (expected == null) {
                    assertEquals(-1, value);
                } else {
                    assertInstanceOf(expected, value);
                }
            } finally {
                stream.close();
                reader.join(Duration.ofSeconds(5));
            }
        }
    }

    private static void awaitWaiting(final Thread reader) {
        await().atMost(Duration.ofSeconds(5)).until(() -> reader.getState() == Thread.State.WAITING);
    }

    private static ByteBuf append(final RequestBodyInputStream stream, final byte... bytes) throws IOException {
        final var buffer = Unpooled.wrappedBuffer(bytes);
        try {
            stream.append(buffer);
        } finally {
            buffer.release();
        }
        return buffer;
    }

    private static void assertRejected(final RequestBodyInputStream stream) {
        final var buffer = Unpooled.buffer(1).writeByte(1);
        try {
            assertThrows(IOException.class, () -> stream.append(buffer));
            assertEquals(1, buffer.refCnt());
            assertThrows(IOException.class, () -> stream.append(Unpooled.EMPTY_BUFFER));
        } finally {
            buffer.release();
        }
    }
}
