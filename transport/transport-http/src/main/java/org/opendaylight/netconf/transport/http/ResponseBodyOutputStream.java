/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An {@link OutputStream} capturing the body of a HTTP response.
 */
public final class ResponseBodyOutputStream extends OutputStream {
    private static final @NonNull ReadOnlyHttpHeaders EMPTY_HEADERS = new ReadOnlyHttpHeaders(false);

    @NonNullByDefault
    private sealed interface State {

        State writeByte(int value) throws IOException;

        State writeBytes(byte[] bytes, int off, int len) throws IOException;

        State flush() throws IOException;

        Closed close(HttpHeaders trailingHeaders) throws IOException;
    }

    /**
     * Initial state: we have status and headers and are waiting to see if there is any body forthcoming. If there is,
     * we switch to {@link FirstChunk}, if there is not, we emit a single {@link FullHttpResponse} with an empty body.
     */
    @NonNullByDefault
    private record EmptyBody(HttpResponseStatus status, HttpHeaders headers) implements State {
        EmptyBody {
            requireNonNull(status);
            requireNonNull(headers);
        }

        @Override
        public FirstChunk writeByte(final int value) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public FirstChunk writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public FirstChunk flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

    /**
     * We have the initial chunk of the body. We are waiting for more data to fill the buffer to its capacity. If we
     * the body does not overflow past the buffer size, we send a single {@link FullHttpResponse} with appropriate
     * {@code Content-Length}. If we overflow, we send out a {@link HttpResponse} with status, specified headers and
     * {@code Transfer-Encoding: chunked}. We then send a {@link HttpContent} message with the chunk and switch to
     * {@link FollowingChunk}.
     *
     * <p>Note that we wait for the buffer to overflow, not become full, as we want to switch to chunked encoding only
     * if the body exceeds the buffer size.
     */
    @NonNullByDefault
    private record FirstChunk(HttpResponseStatus status, HttpHeaders headers, ByteBuf buffer) implements State {
        FirstChunk {
            requireNonNull(status);
            requireNonNull(headers);
            requireNonNull(buffer);
        }

        @Override
        public State writeByte(final int value) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public State writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public FollowingChunk flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

    /**
     * We are in process of sending the second and later body chunks. We acquire data until the buffer is full, which
     * is when we send a {@link HttpContent} message and acquire a new buffer and repeat this until we have been fed
     * the body in its entirety.
     */
    @NonNullByDefault
    private record FollowingChunk(ByteBuf buffer) implements State {
        FollowingChunk {
            requireNonNull(buffer);
        }

        @Override
        public FollowingChunk writeByte(final int value) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public FollowingChunk writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public FollowingChunk flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

    /**
     * Terminal state: the response has been completed and we do not accept any further modifications.
     */
    @NonNullByDefault
    private static final class Closed implements State {
        static final Closed INSTANCE = new Closed();

        private Closed() {
            // Hidden on purpose
        }

        @Override
        public State writeByte(final int value) throws IOException {
            throw eof();
        }

        @Override
        public State writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            throw eof();
        }

        @Override
        public State flush() throws IOException {
            throw eof();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            throw eof();
        }

        private static EOFException eof() {
            return new EOFException("Response already closed");
        }
    }

    // https://www.rfc-editor.org/rfc/rfc9112#name-chunked-transfer-coding
    //
    //    chunked-body   = *chunk
    //                     last-chunk
    //                     trailer-section
    //                     CRLF
    //
    //    chunk          = chunk-size [ chunk-ext ] CRLF
    //                     chunk-data CRLF
    //    chunk-size     = 1*HEXDIG
    //    last-chunk     = 1*("0") [ chunk-ext ] CRLF
    //
    //    chunk-data     = 1*OCTET ; a sequence of chunk-size octets
    //
    // https://www.rfc-editor.org/rfc/rfc9112#name-chunk-extensions
    //
    //    chunk-ext      = *( BWS ";" BWS chunk-ext-name
    //                        [ BWS "=" BWS chunk-ext-val ] )
    //
    //    chunk-ext-name = token
    //    chunk-ext-val  = token / quoted-string
    //
    // https://www.rfc-editor.org/rfc/rfc9112#name-chunked-trailer-section
    //
    //      trailer-section   = *( field-line CRLF )

    private final @NonNull Channel channel;

    private @NonNull State state;

    @NonNullByDefault
    ResponseBodyOutputStream(final Channel channel, final HttpResponseStatus status, final HttpHeaders headers) {
        this.channel = requireNonNull(channel);
        state = new EmptyBody(status, headers);
    }

    @Override
    public void write(final int b) throws IOException {
        state = state.writeByte(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        state = state.writeBytes(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        state = state.flush();
    }

    @Override
    public void close() throws IOException {
        close(EMPTY_HEADERS);
    }

    @NonNullByDefault
    public void close(final HttpHeaders trailingHeaders) throws IOException {
        state = state.close(trailingHeaders);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("state", state).toString();
    }
}
