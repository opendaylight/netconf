/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.server.netty;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;

final class TestUtils {

    private TestUtils() {
        // hidden on purpose
    }

    static int freePort() {
        // find free port
        try {
            final var socket = new ServerSocket(0);
            final var localPort = socket.getLocalPort();
            socket.close();
            return localPort;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static final class TestTransportListener implements TransportChannelListener {
        private final Consumer<Channel> initializer;
        private volatile boolean initialized;

        TestTransportListener() {
            this.initializer = ignored -> { };
        }

        TestTransportListener(final Consumer<Channel> initializer) {
            this.initializer = initializer;
        }

        boolean initialized() {
            return initialized;
        }

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            initializer.accept(channel.channel());
            initialized = true;
        }

        @Override
        public void onTransportChannelFailed(final @NonNull Throwable cause) {
            throw new IllegalStateException("HTTP connection failure", cause);
        }
    }

    static final class TestRequestCallback implements FutureCallback<FullHttpResponse> {
        private volatile boolean completed;
        private volatile FullHttpResponse response;

        @Override
        public void onSuccess(final FullHttpResponse result) {
            // detach response object from channel, so message content is not lost after client is disconnected
            final var content = Unpooled.wrappedBuffer(ByteBufUtil.getBytes(result.content()));
            final var copy = new DefaultFullHttpResponse(result.protocolVersion(), result.status(), content);
            copy.headers().set(result.headers());
            this.response = copy;
            this.completed = true;
        }

        @Override
        public void onFailure(final @NonNull Throwable throwable) {
            this.completed = true;
            throw new IllegalStateException(throwable);
        }

        FullHttpResponse response() {
            return response;
        }

        boolean completed() {
            return completed;
        }
    }

    static final class TestEncryptionService implements AAAEncryptionService {
        // no encryption
        @Override
        public byte[] encrypt(byte[] data) throws GeneralSecurityException {
            return data;
        }

        @Override
        public byte[] decrypt(byte[] encryptedData) throws GeneralSecurityException {
            return encryptedData;
        }
    }

    static final class TestEventListener implements EventStreamListener {
        private volatile boolean started = false;
        private volatile boolean ended = false;
        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);

        @Override
        public void onStreamStart() {
            started = true;
        }

        boolean started() {
            return started;
        }

        @Override
        public void onEventField(@NonNull String fieldName, @NonNull String fieldValue) {
            if ("data".equals(fieldName)) {
                queue.add(fieldValue);
            }
        }

        String readNext() throws InterruptedException {
            return queue.poll(5, TimeUnit.SECONDS);
        }

        @Override
        public void onStreamEnd() {
            ended = true;
        }

        boolean ended() {
            return ended;
        }
    }

    record TestClientStackGrouping(Transport clientTransport) implements HttpClientStackGrouping {

        @Override
        public @NonNull Class<? extends DataContainer> implementedInterface() {
            return HttpClientStackGrouping.class;
        }

        @Override
        public Transport getTransport() {
            return clientTransport;
        }
    }
}
