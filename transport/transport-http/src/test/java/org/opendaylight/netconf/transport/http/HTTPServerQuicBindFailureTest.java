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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.transport.http.TestUtils.generateX509CertData;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

@ExtendWith(MockitoExtension.class)
class HTTPServerQuicBindFailureTest {
    private static final String HTTP3_THREAD_PREFIX = "transport-http3-";

    @Mock
    private TransportChannelListener<HTTPTransportChannel> listener;

    @Test
    void listenQuicReleasesEventLoopGroupOnBindFailure() throws Exception {
        final var loopback = InetAddress.getLoopbackAddress();
        // Occupy a UDP port so the HTTP/3 bind below is forced to fail. Binding the DatagramSocket to ephemeral
        // port 0 and reading getLocalPort() avoids the TCP/UDP race of probing the port with a ServerSocket.
        try (var occupied = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            final var port = occupied.getLocalPort();

            // Snapshot transport-http3- thread IDs BEFORE we trigger listen(), so a parallel-running HTTP/3 test or
            // an already-alive server in the same JVM is not mistaken for a leak from this test.
            final var baselineIds = http3ThreadIds();

            final var certData = generateX509CertData("RSA");
            final var quicCase = HTTPServerOverQuic.of(loopback.getHostAddress(), port, certData.certificate(),
                certData.privateKey(), Uint64.valueOf(65535), Uint64.valueOf(65535),
                Uint32.valueOf(100));

            final var future = HTTPServer.listen(listener, quicCase);
            final var failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            final var bindException = assertInstanceOf(BindException.class, failure.getCause());
            assertEquals("Address already in use", bindException.getMessage());

            // On bind failure the dedicated HTTP/3 EventLoopGroup must be shut down. shutdownGracefully() is
            // asynchronous, so poll briefly until no NEW transport-http3- threads remain beyond the baseline.
            assertTrue(awaitNoNewHttp3Threads(baselineIds, 5_000),
                () -> "Leaked HTTP/3 threads: " + newHttp3Threads(baselineIds));
        }
    }

    @Test
    void listenQuicCreatesAndReleasesEventLoopGroup() throws Exception {
        final var loopback = InetAddress.getLoopbackAddress();
        final var baselineIds = http3ThreadIds();

        final var certData = generateX509CertData("RSA");
        final var quicCase = HTTPServerOverQuic.of(loopback.getHostAddress(), 0, certData.certificate(),
            certData.privateKey(), Uint64.valueOf(65535), Uint64.valueOf(65535), Uint32.valueOf(100));

        final var server = HTTPServer.listen(listener, quicCase).get(5, TimeUnit.SECONDS);
        try {
            // Successful bind must have spun up the dedicated HTTP/3 EventLoopGroup.
            assertFalse(newHttp3Threads(baselineIds).isEmpty(),
                "Expected transport-http3- threads after successful bind");
        } finally {
            server.shutdown().get(5, TimeUnit.SECONDS);
        }

        // shutdown() routes through QuicUnderlay.shutdown(), which calls quicGroup.shutdownGracefully().
        assertTrue(awaitNoNewHttp3Threads(baselineIds, 5_000),
            () -> "Leaked HTTP/3 threads after shutdown: " + newHttp3Threads(baselineIds));
    }


    private static boolean awaitNoNewHttp3Threads(final Set<Long> baselineIds, final long timeoutMillis)
            throws InterruptedException {
        final var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (newHttp3Threads(baselineIds).isEmpty()) {
                return true;
            }
            Thread.sleep(50);
        }
        return newHttp3Threads(baselineIds).isEmpty();
    }

    private static List<String> newHttp3Threads(final Set<Long> baselineIds) {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .filter(thread -> thread.getName().startsWith(HTTP3_THREAD_PREFIX))
            .filter(thread -> !baselineIds.contains(thread.threadId()))
            .map(Thread::getName)
            .toList();
    }

    private static Set<Long> http3ThreadIds() {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .filter(thread -> thread.getName().startsWith(HTTP3_THREAD_PREFIX))
            .map(Thread::threadId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
