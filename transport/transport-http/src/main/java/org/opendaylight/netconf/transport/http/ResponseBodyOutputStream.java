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
import io.netty.handler.codec.http.HttpHeaders;
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

    @NonNullByDefault
    private record EmptyBody(HttpResponseStatus status, HttpHeaders headers) implements State {
        EmptyBody {
            requireNonNull(status);
            requireNonNull(headers);
        }

        @Override
        public First writeByte(final int value) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public First writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public First flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

    @NonNullByDefault
    private record First(HttpResponseStatus status, HttpHeaders headers, ByteBuf buffer) implements State {
        First {
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
        public Following flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

    @NonNullByDefault
    private record Following(ByteBuf buffer) implements State {
        Following {
            requireNonNull(buffer);
        }

        @Override
        public Following writeByte(final int value) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Following writeBytes(final byte[] bytes, final int off, final int len) throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Following flush() throws IOException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        public Closed close(final HttpHeaders trailingHeaders) throws IOException {
            // FIXME: implement this
            return Closed.INSTANCE;
        }
    }

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
