/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.ServerRequestExecutor.formatException;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OutputStream} capturing the body of an HTTP response.
 */
public final class ResponseBodyOutputStream extends OutputStream {
    /**
     * Indirection to handle the various states this stream can be in.
     */
    @NonNullByDefault
    private abstract static sealed class State permits Closed, Open {
        /**
         * Write a single byte to response body.
         *
         * @param value the byte
         * @return the next state
         * @throws IOException if an I/O error occurs
         */
        abstract State writeByte(int value) throws IOException;

        /**
         * Write the specified number of bytes to response body from a buffer, starting at specified offset.
         *
         * @param bytes the buffer
         * @param offset the start offset in the data.
         * @param length the number of bytes to write
         * @return the next state
         * @throws IOException if an I/O error occurs
         */
        abstract State writeBytes(byte[] bytes, int offset, int length) throws IOException;

        /**
         * Flush current outstanding output.
         *
         * @return the next state
         * @throws IOException if an I/O error occurs
         */
        abstract State flush() throws IOException;

        abstract Closed close(ReadOnlyHttpHeaders trailers) throws IOException;

        abstract Closed handleError(RuntimeException exception) throws IOException;

        abstract ToStringHelper addToStringAttributes(ToStringHelper helper);

        @Override
        public final String toString() {
            return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
        }
    }

    /**
     * Terminal state: the response has been completed, and we do not accept any further modifications.
     */
    @NonNullByDefault
    private static final class Closed extends State {
        static final Closed INSTANCE = new Closed();

        private Closed() {
            // Hidden on purpose
        }

        @Override
        State writeByte(final int value) throws IOException {
            throw eof();
        }

        @Override
        State writeBytes(final byte[] bytes, final int offset, final int length) throws IOException {
            throw eof();
        }

        @Override
        State flush() throws IOException {
            throw eof();
        }

        @Override
        Closed close(final ReadOnlyHttpHeaders trailers) throws IOException {
            throw eof();
        }

        @Override
        Closed handleError(final RuntimeException exception) throws IOException {
            throw eof();
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper;
        }

        private static EOFException eof() {
            return new EOFException("Response already closed");
        }
    }

    /**
     * Abstract base class for the non-terminal states.
     */
    @NonNullByDefault
    private abstract static sealed class Open extends State {
        final HTTPServerSession session;
        final ByteBufAllocator alloc;
        final int maxChunkSize;

        Open(final HTTPServerSession session, final ByteBufAllocator alloc, final int maxChunkSize) {
            this.session = requireNonNull(session);
            this.alloc = requireNonNull(alloc);
            if (maxChunkSize < 1) {
                throw new IllegalArgumentException("Chunks have to have at least one byte");
            }
            this.maxChunkSize = maxChunkSize;
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper.add("maxChunkSize", maxChunkSize);
        }
    }

    /**
     * Initial state: we have status and headers and are waiting to see if there is any body forthcoming. If there is,
     * we switch to {@link FirstChunk}, if there is not, we emit a single {@link FullHttpResponse} with an empty body.
     */
    @NonNullByDefault
    private static final class PendingBody extends Open {
        private final HttpResponseStatus status;
        private final ReadOnlyHttpHeaders headers;
        private final ChannelHandlerContext ctx;
        private final HttpVersion version;
        private final @Nullable Integer streamId;

        PendingBody(final HTTPServerSession session,final int maxChunkSize, final HttpResponseStatus status,
                final ReadOnlyHttpHeaders headers,  final ChannelHandlerContext ctx, final HttpVersion version,
                final @Nullable Integer streamId) {
            super(session, ctx.alloc(), maxChunkSize);
            this.status = requireNonNull(status);
            this.headers = requireNonNull(headers);
            this.ctx = requireNonNull(ctx);
            this.version = requireNonNull(version);
            this.streamId = streamId;
        }

        @Override
        State writeByte(final int value) throws IOException {
            LOG.debug("Response has a body of at least one byte");
            return newFirstChunk().writeByte(value);
        }

        @Override
        State writeBytes(final byte[] bytes, final int offset, final int length) throws IOException {
            if (length == 0) {
                return this;
            }
            LOG.debug("Response has a body of at least {} bytes", length);
            return newFirstChunk().writeBytes(bytes, offset, length);
        }

        @Override
        FollowingChunk flush() throws IOException {
            LOG.debug("Flushing response without a determined body");
            return newFirstChunk().flush();
        }

        @Override
        Closed close(final ReadOnlyHttpHeaders trailers) {
            LOG.debug("Closing response with empty body");
            final var response = new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER);
            headers.forEach(entry -> response.headers().add(entry.getKey(), entry.getValue()));
            HTTPServerSession.respond(ctx, streamId, response);
            return Closed.INSTANCE;
        }

        @Override
        Closed handleError(final RuntimeException exception) {
            HTTPServerSession.respond(ctx, streamId, formatException(exception, ctx, version));
            return Closed.INSTANCE;
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper.add("status", status).add("headers", headers));
        }

        private FirstChunk newFirstChunk() {
            return new FirstChunk(session, alloc, maxChunkSize, alloc.buffer(), status, headers, ctx, version,
                streamId);
        }
    }

    /**
     * Abstract base class for states dealing with a response which has a body.
     */
    @NonNullByDefault
    private abstract static sealed class WithBody extends Open permits FirstChunk, FollowingChunk {
        // Constants related to chunked encoding. We use unreleasable buffers so we do not have to worry about
        // reference count. CRLF is not read-only because we always use it as a component in a read-only buffer.
        private static final ByteBuf CRLF = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }));
        private static final ByteBuf LAST_CHUNK = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] { '0', '\r', '\n' })).asReadOnly();

        final ByteBuf buffer;

        WithBody(final HTTPServerSession session, final ByteBufAllocator alloc, final int maxChunkSize,
                final ByteBuf buffer) {
            super(session, alloc, maxChunkSize);
            this.buffer = requireNonNull(buffer);
        }

        /**
         * Create a ByteBuf holding chunk data, formatted according to
         * <a href="https://www.rfc-editor.org/rfc/rfc9112#name-chunked-transfer-coding">Chunked Transfer Coding</a>.
         *
         * @param chunkData chunk data
         * @return A read-only {@link ByteBuf}
         */
        final ByteBuf createChunk(final ByteBuf chunkData) {
            final var chunkSize = chunkData.readableBytes();
            if (chunkSize == 0) {
                // last-chunk     = 1*("0") [ chunk-ext ] CRLF
                chunkData.release();
                return LAST_CHUNK.slice();
            }

            // chunk          = chunk-size [ chunk-ext ] CRLF
            //                  chunk-data CRLF
            // chunk-size     = 1*HEXDIG
            return alloc.compositeBuffer(4).addComponents(true,
                Unpooled.wrappedBuffer(Integer.toHexString(chunkSize).getBytes(StandardCharsets.US_ASCII)),
                CRLF.slice(),
                chunkData,
                CRLF.slice()).asReadOnly();
        }

        @Override
        final WithBody writeBytes(final byte[] bytes, final int offset, final int length) throws IOException {
            return length == 0 ? this : doWriteBytes(bytes, offset, length);
        }

        /**
         * Write the specified number of bytes to response body from a buffer, starting at specified offset. Length is
         * guaranteed to be non-zero.
         *
         * @param bytes the buffer
         * @param offset the start offset in the data.
         * @param length the number of bytes to write
         * @return the next state
         * @throws IOException if an I/O error occurs
         */
        abstract WithBody doWriteBytes(byte[] bytes, int offset, int length) throws IOException;

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper).add("buffer", ByteBufUtil.hexDump(buffer));
        }
    }

    /**
     * We have the initial chunk of the body. We are waiting for more data to fill the buffer to its capacity. If
     * the body does not overflow past the buffer size, we send a single {@link FullHttpResponse} with appropriate
     * {@code Content-Length}. If we overflow, we send out a {@link HttpResponse} with status, specified headers and
     * {@code Transfer-Encoding: chunked}. We then send a {@link HttpContent} message with the chunk and switch to
     * {@link FollowingChunk}.
     *
     * <p>Note that we wait for the buffer to overflow, not become full, as we want to switch to chunked encoding only
     * if the body exceeds the buffer size.
     */
    @NonNullByDefault
    private static final class FirstChunk extends WithBody {
        private final HttpResponseStatus status;
        private final ReadOnlyHttpHeaders headers;
        private final ChannelHandlerContext ctx;
        private final HttpVersion version;
        private final @Nullable Integer streamId;

        FirstChunk(final HTTPServerSession session, final ByteBufAllocator alloc, final int maxChunkSize,
                final ByteBuf buffer, final HttpResponseStatus status, final ReadOnlyHttpHeaders headers,
                final ChannelHandlerContext ctx, final HttpVersion version, final @Nullable Integer streamId) {
            super(session, alloc, maxChunkSize, buffer);
            this.status = requireNonNull(status);
            this.headers = requireNonNull(headers);
            this.ctx = requireNonNull(ctx);
            this.version = requireNonNull(version);
            this.streamId = streamId;
        }

        @Override
        State writeByte(final int value) throws IOException {
            if (buffer.readableBytes() < maxChunkSize) {
                buffer.writeByte(value);
                return this;
            }

            LOG.debug("Response body exceeded {} bytes, sending first chunk", maxChunkSize);
            session.sendResponseStart(status, headers, version);
            session.sendResponsePart(buffer);
            return new FollowingChunk(session, alloc, maxChunkSize, ctx).writeByte(value);
        }

        @Override
        WithBody doWriteBytes(final byte[] bytes, final int offset, final int length) throws IOException {
            final var accept = Math.min(length, maxChunkSize - buffer.readableBytes());
            buffer.writeBytes(bytes, offset, accept);
            final var remaining = length - accept;
            if (remaining == 0) {
                return this;
            }

            LOG.debug("Response body exceeded {} bytes, sending first chunk", maxChunkSize);
            session.sendResponseStart(status, headers, version);
            session.sendResponsePart(buffer);
            return followingChunk().writeBytes(bytes, offset + accept, remaining);
        }

        @Override
        FollowingChunk flush() throws IOException {
            LOG.debug("Forcing chunked response on flush");
            session.sendResponseStart(status, headers, version);
            if (buffer.readableBytes() != 0) {
                session.sendResponsePart(buffer);
            }
            return followingChunk();
        }

        @Override
        Closed close(final ReadOnlyHttpHeaders trailers) {
            final var response = new DefaultFullHttpResponse(version, status, buffer);
            response.headers().add(headers).add(trailers);
            HTTPServerSession.respond(ctx, streamId, response);
            return Closed.INSTANCE;
        }

        @Override
        Closed handleError(final RuntimeException exception) {
            HTTPServerSession.respond(ctx, streamId, formatException(exception, ctx, version));
            return Closed.INSTANCE;
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper.add("status", status).add("headers", headers));
        }

        private FollowingChunk followingChunk() {
            return new FollowingChunk(session, alloc, maxChunkSize, ctx);
        }
    }

    /**
     * We are in process of sending the second and later body chunks. We acquire data until the buffer is full, which
     * is when we send a {@link HttpContent} message and acquire a new buffer and repeat this until we have been fed
     * the body in its entirety.
     */
    @NonNullByDefault
    private static final class FollowingChunk extends WithBody {
        private final ChannelHandlerContext ctx;

        FollowingChunk(final HTTPServerSession session, final ByteBufAllocator alloc, final int maxChunkSize,
            final ChannelHandlerContext ctx) {
            super(session, alloc, maxChunkSize, alloc.buffer());
            this.ctx = ctx;
        }

        @Override
        FollowingChunk writeByte(final int value) throws IOException {
            buffer.writeByte(value);
            if (buffer.readableBytes() < maxChunkSize) {
                return this;
            }

            LOG.debug("Response chunk reached {} bytes, sending it", maxChunkSize);
            session.sendResponsePart(buffer);
            return new FollowingChunk(session, alloc, maxChunkSize, ctx);
        }

        @Override
        WithBody doWriteBytes(final byte[] bytes, final int offset, final int length) throws IOException {
            final var accept = Math.min(length, maxChunkSize - buffer.readableBytes());
            buffer.writeBytes(bytes, offset, accept);
            if (buffer.readableBytes() < maxChunkSize) {
                return this;
            }
            LOG.debug("Response chunk reached {} bytes, sending it", maxChunkSize);
            session.sendResponsePart(buffer);
            return new FollowingChunk(session, alloc, maxChunkSize, ctx)
                .writeBytes(bytes, offset + accept, length - accept);
        }

        @Override
        FollowingChunk flush() throws IOException {
            final var size = buffer.readableBytes();
            if (size == 0) {
                return this;
            }

            LOG.debug("Flushing {}-byte chunk", size);
            session.sendResponsePart(buffer);
            return new FollowingChunk(session, alloc, maxChunkSize, ctx);
        }

        @Override
        Closed close(final ReadOnlyHttpHeaders trailers) throws IOException {
            if (buffer.readableBytes() != 0) {
                session.sendResponsePart(buffer);
            }
            session.sendResponseEnd(trailers);
            return Closed.INSTANCE;
        }

        @Override
        Closed handleError(final RuntimeException exception) {
            ctx.channel().close();
            return Closed.INSTANCE;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ResponseBodyOutputStream.class);
    // FIXME: make maxChunkSize tunable
    private static final int MAX_CHUNK_SIZE = 256 * 1024;

    private @NonNull State state;

    @NonNullByDefault
    ResponseBodyOutputStream(final ChannelHandlerContext ctx, final HttpResponseStatus status,
            final ReadOnlyHttpHeaders headers, final HttpVersion version, final @Nullable Integer streamId) {
        this(ctx, status, headers, version, streamId, MAX_CHUNK_SIZE);
    }

    @NonNullByDefault
    ResponseBodyOutputStream(final ChannelHandlerContext ctx, final HttpResponseStatus status,
            final ReadOnlyHttpHeaders headers, final HttpVersion version, final @Nullable Integer streamId,
            final int maxChunkSize) {
        state = new PendingBody(ctx.pipeline().get(HTTPServerSession.class), maxChunkSize, status,
            headers, ctx, version, streamId);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public void write(final int b) {
        try {
            state = state.writeByte(b);
        } catch (IOException e) {
            LOG.debug("Error occurred when trying to write byte: ", e);
            state = Closed.INSTANCE;
        }
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public void write(final byte[] b, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        state = state.writeBytes(b, off, len);
    }

    @Override
    public void flush() {
        try {
            state = state.flush();
        } catch (IOException e) {
            LOG.debug("Error occurred during flushing: ", e);
            state = Closed.INSTANCE;
        }
    }

    @Override
    public void close() {
        close(HeadersResponse.EMPTY_HEADERS);
    }

    @NonNullByDefault
    public void close(final ReadOnlyHttpHeaders trailers) {
        try {
            state = state.close(trailers);
        } catch (IOException e) {
            LOG.debug("Error occurred during closing: ", e);
            state = Closed.INSTANCE;
        }
    }

    public void handleError(final RuntimeException exception) throws IOException {
        state = state.handleError(exception);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("state", state).toString();
    }
}
