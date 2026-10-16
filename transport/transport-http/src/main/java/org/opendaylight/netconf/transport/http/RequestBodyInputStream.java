/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An HTTP request body delivered incrementally by a single producer and read by a single consumer.
 *
 * <p>The channel handler creates this stream when it accepts the request head and submits it to
 * {@link PendingRequest#execute(PendingRequestListener, InputStream)} on the request executor. On the event loop it
 * calls {@link #append(ByteBuf)} for each content buffer (including any bytes in the last content), then
 * {@link #complete()} for the last content, or {@link #fail(Throwable)} on a transport error. Reads may wait for
 * content; producer methods never wait for the consumer to read. Flow control and request size limits belong to the
 * handler: this stream does not bound the amount of queued content.
 *
 * <p>The handler must release its incoming content references as usual. This stream retains its own slices and releases
 * them as they are consumed or discarded. The handler must close or cancel the stream if processing ends early or
 * the request cannot be submitted. Closing also wakes a waiting reader. Interrupting a waiting reader aborts the
 * stream and restores the reader's interrupt status.
 */
public final class RequestBodyInputStream extends InputStream {
    /**
     * Lifecycle decisions only. The stream owns the queue and invokes all state methods with its lock held.
     */
    @NonNullByDefault
    private sealed interface State permits Lifecycle, Failed, Interrupted {
        boolean acceptsContent() throws IOException;

        // Check whether reads are permitted and whether an empty queue should wait instead of returning EOF.
        boolean awaitMore() throws IOException;

        default boolean retainsContent() {
            return false;
        }

        default State complete() {
            return this;
        }

        default State fail(final Throwable cause) {
            return this;
        }

        default State cancel() {
            return this;
        }

        default State close() {
            return this;
        }

        default State interrupt(final InterruptedException cause) {
            return this;
        }
    }

    /**
     * States without a failure cause. Completion preserves unread bytes; closing discards them. An early consumer
     * close enters DISCARDING until producer completion establishes the boundary after which appends are errors.
     */
    @NonNullByDefault
    private enum Lifecycle implements State {
        OPEN,
        COMPLETED,
        DISCARDING,
        CLOSED;

        @Override
        public boolean acceptsContent() throws IOException {
            return switch (this) {
                case OPEN -> true;
                case DISCARDING -> false;
                case COMPLETED, CLOSED -> throw new EOFException("Request body already completed");
            };
        }

        @Override
        public boolean awaitMore() throws IOException {
            return switch (this) {
                case OPEN -> true;
                case COMPLETED -> false;
                case DISCARDING, CLOSED -> throw new IOException("Request body closed");
            };
        }

        @Override
        public boolean retainsContent() {
            return this == OPEN || this == COMPLETED;
        }

        @Override
        public State complete() {
            return switch (this) {
                case OPEN -> COMPLETED;
                case DISCARDING -> CLOSED;
                case COMPLETED, CLOSED -> this;
            };
        }

        @Override
        public State fail(final Throwable cause) {
            return this == OPEN ? new Failed("Request body failed", cause) : this;
        }

        @Override
        public State cancel() {
            return retainsContent()
                ? new Failed("Request body cancelled", new CancellationException("Request cancelled")) : this;
        }

        @Override
        public State close() {
            return switch (this) {
                case OPEN -> DISCARDING;
                case COMPLETED -> CLOSED;
                case DISCARDING, CLOSED -> this;
            };
        }

        @Override
        public State interrupt(final InterruptedException cause) {
            return retainsContent() ? new Interrupted(cause) : this;
        }
    }

    /**
     * Retain the original cause, creating a fresh exception for each operation. Later terminal signals are ignored.
     */
    @NonNullByDefault
    private record Failed(String message, Throwable cause) implements State {
        Failed {
            requireNonNull(message);
            requireNonNull(cause);
        }

        @Override
        public boolean acceptsContent() throws IOException {
            throw new IOException(message, cause);
        }

        @Override
        public boolean awaitMore() throws IOException {
            throw new IOException(message, cause);
        }
    }

    @NonNullByDefault
    private record Interrupted(InterruptedException cause) implements State {
        Interrupted {
            requireNonNull(cause);
        }

        @Override
        public boolean acceptsContent() throws IOException {
            throw exception();
        }

        @Override
        public boolean awaitMore() throws IOException {
            throw exception();
        }

        private InterruptedIOException exception() {
            final var exception = new InterruptedIOException("Interrupted waiting for request body");
            exception.initCause(cause);
            return exception;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    // All mutable state, including the buffers' reader indices, is protected by lock.
    private final ArrayDeque<ByteBuf> buffers = new ArrayDeque<>();

    private @NonNull State state = Lifecycle.OPEN;

    /**
     * Append the readable portion of a buffer without changing its indices. The caller keeps ownership of its reference
     * and must not modify the appended bytes until consumed. Empty buffers are accepted without retaining them.
     *
     * @param buffer incoming content
     * @return {@code true} if accepted, {@code false} if the consumer closed the stream before producer completion;
     *         no reference is retained when returning {@code false}, and the caller must discard its content
     * @throws IOException if the producer has completed, or the stream has failed or been cancelled;
     *         no reference is retained
     */
    public boolean append(final ByteBuf buffer) throws IOException {
        requireNonNull(buffer);
        lock.lock();
        try {
            if (!state.acceptsContent()) {
                return false;
            }
            if (buffer.isReadable()) {
                buffers.addLast(buffer.retainedSlice());
                changed.signalAll();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Signal normal completion. Queued bytes remain readable, followed by EOF. Repeated terminal signals are ignored.
     */
    public void complete() {
        lock.lock();
        try {
            transitionTo(state.complete());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fail the request, discarding queued bytes and waking the reader with an {@link IOException} carrying the cause.
     * Repeated terminal signals, including failure after normal completion, are ignored.
     *
     * @param cause request failure
     */
    public void fail(final Throwable cause) {
        requireNonNull(cause);
        lock.lock();
        try {
            transitionTo(state.fail(cause));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancel consumption, discarding queued bytes and waking the reader. May also discard a completed body's unread
     * bytes. An earlier failure or cancellation is preserved.
     */
    public void cancel() {
        lock.lock();
        try {
            transitionTo(state.cancel());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read() throws IOException {
        lock.lock();
        try {
            if (!awaitContent()) {
                return -1;
            }
            final var buffer = buffers.getFirst();
            final int result = buffer.readUnsignedByte();
            releaseIfConsumed(buffer);
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(final byte[] bytes, final int offset, final int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        lock.lock();
        try {
            if (!awaitContent()) {
                return -1;
            }
            int copied = 0;
            do {
                final var buffer = buffers.getFirst();
                final int count = Math.min(length - copied, buffer.readableBytes());
                buffer.readBytes(bytes, offset + copied, count);
                copied += count;
                releaseIfConsumed(buffer);
            } while (copied < length && !buffers.isEmpty());
            // Return the available bytes without waiting to fill the caller's entire buffer.
            return copied;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            transitionTo(state.close());
        } finally {
            lock.unlock();
        }
    }

    private boolean awaitContent() throws IOException {
        while (true) {
            final var awaitMore = state.awaitMore();
            if (!buffers.isEmpty()) {
                return true;
            }
            if (!awaitMore) {
                return false;
            }
            try {
                changed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // The state preserves any failure which raced with the interrupt during lock reacquisition.
                transitionTo(state.interrupt(e));
            }
        }
    }

    private void releaseIfConsumed(final ByteBuf buffer) {
        if (!buffer.isReadable()) {
            buffers.removeFirst().release();
        }
    }

    private void transitionTo(final State next) {
        if (state == next) {
            return;
        }
        state = next;
        if (!next.retainsContent()) {
            ByteBuf buffer;
            while ((buffer = buffers.pollFirst()) != null) {
                buffer.release();
            }
        }
        changed.signalAll();
    }
}
