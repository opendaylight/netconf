/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.Transport;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Integration test of HTTP/1.1 request streaming: a real {@link HTTPServer} listening on the loopback interface,
 * talked to over plain sockets so that the test is in full control of what goes onto the wire and when.
 *
 * <p>The server runs on a single event loop thread, hence anything stalling that thread on behalf of one connection
 * would show up as every other connection stalling as well. Its connections allocate from a dedicated allocator, whose
 * accounting has to be back at zero once the connections are gone: that is the {@code ByteBuf} leak check applied to
 * every test here.
 *
 * <p>Every test runs under a deadline, so that a server which stops delivering content, or a consumer which is never
 * woken, fails the test instead of hanging the build.
 */
@Timeout(20)
class Http1StreamingIntegrationTest {
    /**
     * The request-phase work of a {@link PendingRequest}: consume the body and produce a response, or throw to signal
     * a failure of whatever is parsing the body.
     */
    @FunctionalInterface
    private interface BodyHandler {
        Response handle(@Nullable InputStream body) throws Exception;
    }

    private static final class TestRequest extends PendingRequest<Object> {
        private final BodyHandler handler;

        TestRequest(final BodyHandler handler) {
            this.handler = handler;
        }

        @Override
        @SuppressWarnings("checkstyle:illegalCatch")
        public void execute(final PendingRequestListener listener, final InputStream body) {
            final Response response;
            try {
                response = handler.handle(body);
            } catch (Exception e) {
                listener.requestFailed(this, e);
                return;
            } finally {
                closeAggregated(body);
            }
            listener.requestComplete(this, response);
        }

        @Override
        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper;
        }
    }

    private record RawResponse(int status, Map<String, String> headers, String body) {
        // Nothing else
    }

    /**
     * A bare-bones HTTP/1.1 client, which leaves request framing to its caller.
     */
    private static final class RawClient implements AutoCloseable {
        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;

        RawClient(final Socket socket) throws IOException {
            this.socket = socket;
            // a server which stops responding fails the test instead of hanging it
            socket.setSoTimeout(10_000);
            socket.setTcpNoDelay(true);
            out = new BufferedOutputStream(socket.getOutputStream(), 2 * CHUNK);
            in = new BufferedInputStream(socket.getInputStream());
        }

        void send(final String str) throws IOException {
            out.write(str.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        void sendBytes(final byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        void sendChunk(final byte[] bytes) throws IOException {
            out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.write('\r');
            out.write('\n');
            out.flush();
        }

        RawResponse readResponse() throws IOException {
            final var statusLine = readLine();
            assertNotNull(statusLine, "connection closed without a response");
            final var headers = new HashMap<String, String>();
            for (var line = readLine(); line != null && !line.isEmpty(); line = readLine()) {
                final int colon = line.indexOf(':');
                headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
            final var body = in.readNBytes(Integer.parseInt(headers.getOrDefault("content-length", "0")));
            return new RawResponse(Integer.parseInt(statusLine.split(" ")[1]), headers,
                new String(body, StandardCharsets.UTF_8));
        }

        /**
         * {@return whether the server has closed the connection without sending anything further}
         */
        boolean isClosedByServer() throws IOException {
            try {
                return in.read() == -1;
            } catch (SocketException e) {
                return true;
            }
        }

        private @Nullable String readLine() throws IOException {
            final var line = new ByteArrayOutputStream();
            while (true) {
                final int ch = in.read();
                if (ch == -1) {
                    return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
                }
                if (ch == '\n') {
                    return line.toString(StandardCharsets.US_ASCII).stripTrailing();
                }
                line.write(ch);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final String CHUNKED_HEAD = """
        POST /upload HTTP/1.1\r
        Host: localhost\r
        Transfer-Encoding: chunked\r
        \r
        """;
    private static final String LAST_CHUNK = "0\r\n\r\n";
    private static final int CHUNK = 64 * 1024;
    private static final int MEGABYTE = 1024 * 1024;
    // 104 MiB, i.e. comfortably more than 100 MB whichever way that is counted
    private static final int LARGE_PAYLOAD = 104 * MEGABYTE;

    private static BootstrapFactory bootstrapFactory;
    private static String localAddress;

    private final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
    private final ChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final Queue<Class<?>> observed = new ConcurrentLinkedQueue<>();
    private final Queue<Throwable> transportFailures = new ConcurrentLinkedQueue<>();
    private final List<RawClient> clients = new CopyOnWriteArrayList<>();

    private AuthHandlerFactory authHandlerFactory;
    private HTTPServer server;
    private int port;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("streaming-it", 1);
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
    }

    @AfterEach
    void afterEach() throws Exception {
        for (var client : clients) {
            client.close();
        }
        if (server != null) {
            server.shutdown().get(5, TimeUnit.SECONDS);
        }
        connections.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
        assertEquals(List.of(), List.copyOf(transportFailures));

        // ByteBuf lifecycle: with the connections gone, nothing they allocated may remain unreleased
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(0, alloc.metric().usedDirectMemory(), "leaked direct buffers");
            assertEquals(0, alloc.metric().usedHeapMemory(), "leaked heap buffers");
        });
    }

    /**
     * The server starts consuming a chunked upload of more than 100 MB while the client is still at its beginning,
     * and delivers all of it intact without the request ever existing as a {@link FullHttpRequest}.
     */
    @Test
    // by far the largest transfer here: allow for a slow, loaded build machine
    @Timeout(60)
    void uploadLargerThan100MbIsStreamed() throws Exception {
        final var megabyteConsumed = new CountDownLatch(1);
        startServer(128 * MEGABYTE, body -> {
            final var crc = new CRC32();
            final var bytes = new byte[CHUNK];
            long count = 0;
            int read;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
                if (count >= MEGABYTE) {
                    megabyteConsumed.countDown();
                }
            }
            return digestResponse(count, crc);
        });

        final var client = connect();
        final var block = block(1);
        final var sent = new CRC32();
        client.send(CHUNKED_HEAD);
        upload(client, block, 0, MEGABYTE / CHUNK, sent);
        // the request is nowhere near complete, yet its body is already being processed
        assertTrue(megabyteConsumed.await(5, TimeUnit.SECONDS));
        upload(client, block, MEGABYTE / CHUNK, LARGE_PAYLOAD / CHUNK, sent);
        client.send(LAST_CHUNK);

        final var response = client.readResponse();
        assertEquals(200, response.status());
        assertTrue(LARGE_PAYLOAD > 100_000_000);
        assertEquals(digestText(LARGE_PAYLOAD, sent), response.body());
        assertEquals(List.of(StreamingHttpRequest.class), List.copyOf(observed));
    }

    @Test
    void contentLengthUploadIsAggregated() throws Exception {
        startServer(128 * MEGABYTE, Http1StreamingIntegrationTest::digest);

        final var client = connect();
        final var block = block(2);
        final var sent = new CRC32();
        client.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + MEGABYTE + "\r\n\r\n");
        for (int i = 0; i < MEGABYTE / CHUNK; ++i) {
            stamp(block, i);
            sent.update(block);
            client.sendBytes(block);
        }

        final var response = client.readResponse();
        assertEquals(200, response.status());
        assertEquals(digestText(MEGABYTE, sent), response.body());
        // the session saw a single, complete request
        final var requests = List.copyOf(observed);
        assertEquals(1, requests.size());
        assertTrue(FullHttpRequest.class.isAssignableFrom(requests.getFirst()), requests.toString());
    }

    /**
     * Uploads on separate connections are all in progress at once and do not get into each other's way: each consumer
     * sees exactly the bytes of its own connection.
     */
    @Test
    void concurrentChunkedUploadsAreIndependent() throws Exception {
        final int uploads = 8;
        final int chunks = 4 * MEGABYTE / CHUNK;
        final var allUploading = new CyclicBarrier(uploads);
        startServer(8 * MEGABYTE, body -> {
            final var crc = new CRC32();
            final var first = body.readNBytes(CHUNK);
            crc.update(first);
            // every single upload is part-way through its body here
            allUploading.await(10, TimeUnit.SECONDS);

            final var bytes = new byte[8192];
            long count = first.length;
            int read;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
            }
            return digestResponse(count, crc);
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var results = new ArrayList<Future<?>>();
            for (int i = 0; i < uploads; ++i) {
                final int id = i;
                results.add(executor.submit(() -> {
                    final var client = connect();
                    final var block = block(100 + id);
                    final var sent = new CRC32();
                    client.send(CHUNKED_HEAD);
                    upload(client, block, 0, chunks, sent);
                    client.send(LAST_CHUNK);

                    final var response = client.readResponse();
                    assertEquals(200, response.status());
                    assertEquals(digestText((long) chunks * CHUNK, sent), response.body());
                    return null;
                }));
            }
            for (var result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        }

        final var requests = List.copyOf(observed);
        assertEquals(uploads, requests.size());
        assertTrue(requests.stream().allMatch(StreamingHttpRequest.class::equals), requests.toString());
    }

    /**
     * A consumer which stops reading does not stop the transport: its client gets to send the entire request, other
     * connections sharing the event loop are served in the meantime, and the body is intact once the consumer does
     * get around to reading it.
     */
    @Test
    void slowConsumerDoesNotStallTransport() throws Exception {
        final int chunks = 16 * MEGABYTE / CHUNK;
        final var requests = new AtomicInteger();
        final var stalled = new CountDownLatch(1);
        final var resume = new CountDownLatch(1);
        startServer(32 * MEGABYTE, body -> {
            if (requests.getAndIncrement() != 0) {
                return digest(body);
            }

            final var crc = new CRC32();
            final var first = body.readNBytes(CHUNK);
            crc.update(first);
            stalled.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));

            final var bytes = new byte[1021];
            long count = first.length;
            int read;
            int reads = 0;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
                if (++reads % 512 == 0) {
                    Thread.sleep(1);
                }
            }
            return digestResponse(count, crc);
        });

        final var slowClient = connect();
        final var sent = new CRC32();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var uploaded = executor.submit(() -> {
                slowClient.send(CHUNKED_HEAD);
                upload(slowClient, block(3), 0, chunks, sent);
                slowClient.send(LAST_CHUNK);
                return null;
            });
            assertTrue(stalled.await(5, TimeUnit.SECONDS));
            // far more than socket buffers could absorb: the server keeps reading while the consumer does not
            uploaded.get(10, TimeUnit.SECONDS);
        }

        final var otherClient = connect();
        otherClient.send(CHUNKED_HEAD + "5\r\nhello\r\n" + LAST_CHUNK);
        final var otherResponse = otherClient.readResponse();
        assertEquals(200, otherResponse.status());
        assertEquals(digestText("hello"), otherResponse.body());

        resume.countDown();
        final var response = slowClient.readResponse();
        assertEquals(200, response.status());
        assertEquals(digestText((long) chunks * CHUNK, sent), response.body());
    }

    @Test
    void chunkedUploadOverLimitIsRejectedWith413() throws Exception {
        final var requests = new AtomicInteger();
        final var limitConsumed = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        startServer(MEGABYTE, body -> {
            if (requests.getAndIncrement() != 0) {
                return digest(body);
            }

            assertEquals(MEGABYTE, body.readNBytes(MEGABYTE).length);
            limitConsumed.countDown();
            try {
                body.read();
                throw new AssertionError("Content beyond the limit reached the consumer");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        });

        final var client = connect();
        final var block = block(4);
        client.send(CHUNKED_HEAD);
        upload(client, block, 0, MEGABYTE / CHUNK, new CRC32());
        assertTrue(limitConsumed.await(5, TimeUnit.SECONDS));
        // a single byte beyond the limit
        client.send("1\r\nX");

        final var response = client.readResponse();
        assertEquals(413, response.status());
        assertEquals("close", response.headers().get("connection"));
        assertTrue(client.isClosedByServer());
        assertNotNull(failure.get(5, TimeUnit.SECONDS));

        // a body of exactly the limit is fine
        final var nextClient = connect();
        final var sent = new CRC32();
        nextClient.send(CHUNKED_HEAD);
        upload(nextClient, block, 0, MEGABYTE / CHUNK, sent);
        nextClient.send(LAST_CHUNK);
        final var nextResponse = nextClient.readResponse();
        assertEquals(200, nextResponse.status());
        assertEquals(digestText(MEGABYTE, sent), nextResponse.body());
    }

    @Test
    void malformedChunkIsRejectedWith400() throws Exception {
        final var consumed = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        startServer(MEGABYTE, body -> {
            assertEquals("abc", new String(body.readNBytes(3), StandardCharsets.US_ASCII));
            consumed.countDown();
            try {
                body.read();
                throw new AssertionError("Malformed content reached the consumer");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        });

        final var client = connect();
        client.send(CHUNKED_HEAD + "3\r\nabc\r\n");
        assertTrue(consumed.await(5, TimeUnit.SECONDS));
        // not a chunk size
        client.send("zz\r\n");

        final var response = client.readResponse();
        assertEquals(400, response.status());
        assertEquals("close", response.headers().get("connection"));
        assertTrue(client.isClosedByServer());
        assertNotNull(failure.get(5, TimeUnit.SECONDS));
    }

    @Test
    void unsupportedTransferCodingIsRejectedWith501() throws Exception {
        final var requests = new AtomicInteger();
        startServer(MEGABYTE, body -> {
            requests.incrementAndGet();
            return digest(body);
        });

        final var client = connect();
        client.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip, chunked\r\n\r\n");

        final var response = client.readResponse();
        assertEquals(501, response.status());
        assertTrue(client.isClosedByServer());
        assertEquals(0, requests.get());
        assertEquals(List.of(), List.copyOf(observed));
    }

    /**
     * A client disconnecting mid-upload fails the consumer waiting for the rest of the body, and leaves the server
     * serving other clients.
     */
    @Test
    void clientDisconnectFailsPendingUpload() throws Exception {
        final var requests = new AtomicInteger();
        final var consumed = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        startServer(MEGABYTE, body -> {
            if (requests.getAndIncrement() != 0) {
                return digest(body);
            }

            assertEquals("abc", new String(body.readNBytes(3), StandardCharsets.US_ASCII));
            consumed.countDown();
            try {
                body.read();
                throw new AssertionError("Body of a disconnected client completed");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        });

        final var client = connect();
        client.send(CHUNKED_HEAD + "3\r\nabc\r\n");
        assertTrue(consumed.await(5, TimeUnit.SECONDS));
        client.close();
        assertNotNull(failure.get(5, TimeUnit.SECONDS));

        final var nextClient = connect();
        nextClient.send(CHUNKED_HEAD + "5\r\nhello\r\n" + LAST_CHUNK);
        final var response = nextClient.readResponse();
        assertEquals(200, response.status());
        assertEquals(digestText("hello"), response.body());
    }

    /**
     * A consumer which cannot parse the body is answered before the upload completes. The client finishes the upload
     * anyway, and then reuses the connection.
     */
    @Test
    void parserFailureIsAnsweredBeforeUploadCompletes() throws Exception {
        final var requests = new AtomicInteger();
        startServer(MEGABYTE, body -> {
            if (requests.getAndIncrement() != 0) {
                return digest(body);
            }
            assertEquals("{x!", new String(body.readNBytes(3), StandardCharsets.US_ASCII));
            throw new IllegalArgumentException("Malformed document");
        });

        final var client = connect();
        client.send(CHUNKED_HEAD + "3\r\n{x!\r\n");
        final var failed = client.readResponse();
        assertEquals(500, failed.status());
        assertEquals("Malformed document", failed.body());

        // the rest of the rejected request is discarded by the server
        upload(client, block(5), 0, 4, new CRC32());
        client.send(LAST_CHUNK);

        client.send(CHUNKED_HEAD + "5\r\nhello\r\n" + LAST_CHUNK);
        final var response = client.readResponse();
        assertEquals(200, response.status());
        assertEquals(digestText("hello"), response.body());
        assertEquals(List.of(StreamingHttpRequest.class, StreamingHttpRequest.class), List.copyOf(observed));
    }

    @Test
    void keepAliveReusesConnectionAcrossFramings() throws Exception {
        startServer(MEGABYTE, Http1StreamingIntegrationTest::digest);
        final var client = connect();

        client.send(CHUNKED_HEAD + "3\r\nabc\r\n2\r\nde\r\n" + LAST_CHUNK);
        assertDigest("abcde", client.readResponse());

        client.send("PUT /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\n\r\nbody");
        assertDigest("body", client.readResponse());

        // a chunked request need not have a body at all
        client.send(CHUNKED_HEAD + LAST_CHUNK);
        assertDigest("", client.readResponse());

        client.send("GET /upload HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertDigest("", client.readResponse());

        client.send("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            Transfer-Encoding: chunked\r
            \r
            4\r
            done\r
            """ + LAST_CHUNK);
        final var last = client.readResponse();
        assertDigest("done", last);
        assertEquals("close", last.headers().get("connection"));
        assertTrue(client.isClosedByServer());

        final var requests = List.copyOf(observed);
        assertEquals(5, requests.size());
        assertEquals(3, requests.stream().filter(StreamingHttpRequest.class::equals).count());
        assertEquals(2, requests.stream().filter(FullHttpRequest.class::isAssignableFrom).count());
    }

    /**
     * An aggregated body is the consumer's to close, which is what releases the aggregated buffer. A streamed body is
     * deliberately left alone: releasing whatever the consumer has not read is up to the transport.
     */
    private static void closeAggregated(final @Nullable InputStream body) {
        if (body != null && !(body instanceof RequestBodyInputStream)) {
            try {
                body.close();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
     * The TLS pipeline is assembled by a different code path than the cleartext one, and has to stream just the same.
     */
    @Test
    void chunkedUploadOverTlsIsStreamed() throws Exception {
        final var certData = TestUtils.generateX509CertData("RSA");
        port = TestUtils.freePort();
        startServer(HTTPServerOverTls.of(localAddress, port, certData.certificate(), certData.privateKey()),
            8 * MEGABYTE, Http1StreamingIntegrationTest::digest);

        final var client = connectTls();
        final var sent = new CRC32();
        final int chunks = 4 * MEGABYTE / CHUNK;
        client.send(CHUNKED_HEAD);
        upload(client, block(6), 0, chunks, sent);
        client.send(LAST_CHUNK);
        assertEquals(digestText((long) chunks * CHUNK, sent), okBody(client.readResponse()));

        // and the connection is still good for an aggregated request
        client.send("PUT /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\n\r\nbody");
        assertDigest("body", client.readResponse());

        final var requests = List.copyOf(observed);
        assertEquals(2, requests.size());
        assertEquals(StreamingHttpRequest.class, requests.getFirst());
        assertTrue(FullHttpRequest.class.isAssignableFrom(requests.getLast()), requests.toString());
    }

    /**
     * A streamed request never reaches HttpObjectAggregator, which would otherwise answer its expectation.
     */
    @Test
    void expectContinueIsAnsweredBeforeBodyIsSent() throws Exception {
        startServer(MEGABYTE, Http1StreamingIntegrationTest::digest);

        final var client = connect();
        client.send("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Expect: 100-continue\r
            Transfer-Encoding: chunked\r
            \r
            """);
        // nothing of the body has been sent: the client is waiting to be told to go ahead
        assertEquals(100, client.readResponse().status());

        client.send("5\r\nhello\r\n" + LAST_CHUNK);
        assertDigest("hello", client.readResponse());
        assertEquals(List.of(StreamingHttpRequest.class), List.copyOf(observed));
    }

    /**
     * Chunk extensions and trailer fields are framing: they are neither delivered to the consumer nor counted
     * towards the body limit, which the content of this request reaches exactly.
     */
    @Test
    void chunkExtensionsAndTrailersAreNotContent() throws Exception {
        startServer(10, Http1StreamingIntegrationTest::digest);

        final var client = connect();
        client.send(CHUNKED_HEAD + "5;name=value\r\nhello\r\n5;other=\"quoted value\"\r\nworld\r\n"
            + "0\r\nX-Checksum: 0123456789abcdef\r\nX-Other: trailer\r\n\r\n");
        assertDigest("helloworld", client.readResponse());

        // the trailers have been consumed in full: the connection is in sync for the next request
        client.send(CHUNKED_HEAD + "3\r\nabc\r\n" + LAST_CHUNK);
        assertDigest("abc", client.readResponse());
    }

    /**
     * Framing which arrives a byte at a time, so that chunk-size lines, chunk data and their delimiters are all split
     * across reads, decodes to the same body.
     */
    @Test
    void requestDribbledByteByByteIsReassembled() throws Exception {
        startServer(MEGABYTE, Http1StreamingIntegrationTest::digest);

        final var client = connect();
        final var content = new StringBuilder();
        final var wire = new StringBuilder(CHUNKED_HEAD);
        for (int i = 1; i <= 20; ++i) {
            final var piece = "piece-" + i + ";";
            content.append(piece);
            wire.append(Integer.toHexString(piece.length())).append("\r\n").append(piece).append("\r\n");
        }
        wire.append(LAST_CHUNK);
        for (var octet : wire.toString().getBytes(StandardCharsets.US_ASCII)) {
            client.sendBytes(new byte[] { octet });
        }

        assertDigest(content.toString(), client.readResponse());
        assertEquals(List.of(StreamingHttpRequest.class), List.copyOf(observed));
    }

    /**
     * A long-lived connection keeps switching between streaming and aggregation without accumulating state: every
     * request is answered with its own content, and nothing is left allocated once the connection is closed.
     */
    @Test
    void manyRequestsOnOneConnection() throws Exception {
        startServer(MEGABYTE, Http1StreamingIntegrationTest::digest);

        final var client = connect();
        final int requests = 200;
        for (int i = 0; i < requests; ++i) {
            final var content = "request-" + i;
            if (i % 2 == 0) {
                client.send(CHUNKED_HEAD + Integer.toHexString(content.length()) + "\r\n" + content + "\r\n"
                    + LAST_CHUNK);
            } else {
                client.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + content.length()
                    + "\r\n\r\n" + content);
            }
            assertDigest(content, client.readResponse());
        }

        final var seen = List.copyOf(observed);
        assertEquals(requests, seen.size());
        assertEquals(requests / 2, seen.stream().filter(StreamingHttpRequest.class::equals).count());
        assertEquals(requests / 2, seen.stream().filter(FullHttpRequest.class::isAssignableFrom).count());
    }

    /**
     * Authentication happens on the request headers, i.e. before any of a streamed body is looked at: a rejected
     * upload never reaches a consumer, while an authenticated one is streamed as usual.
     */
    @Test
    void unauthenticatedChunkedUploadNeverReachesConsumer() throws Exception {
        final var requests = new AtomicInteger();
        // installed ahead of the session, the way RestconfSession does it
        authHandlerFactory = () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return "username".equals(username) && "pa$$W0rd".equals(password) ? username : null;
            }
        };
        startServer(MEGABYTE, body -> {
            requests.incrementAndGet();
            return digest(body);
        });

        final var rejected = connect();
        rejected.send("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Authorization: %s\r
            Transfer-Encoding: chunked\r
            \r
            """.formatted(TestUtils.basicAuthHeader("username", "wrong")));
        assertEquals(401, rejected.readResponse().status());
        assertEquals(0, requests.get());

        final var client = connect();
        client.send("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Authorization: %s\r
            Transfer-Encoding: chunked\r
            \r
            5\r
            hello\r
            """.formatted(TestUtils.basicAuthHeader("username", "pa$$W0rd")) + LAST_CHUNK);
        assertDigest("hello", client.readResponse());
        assertEquals(1, requests.get());
        // the session has only ever seen the authenticated request
        assertEquals(List.of(StreamingHttpRequest.class), List.copyOf(observed));
    }

    /**
     * Framing which leaves the length of the body open to interpretation is refused outright.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "POST /upload HTTP/1.0\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n",
        "POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nContent-Length: 5\r\n\r\n",
        "POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked, chunked\r\n\r\n",
        "POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip\r\n\r\n"
    })
    void ambiguousFramingIsRejectedWith400(final String head) throws Exception {
        final var requests = new AtomicInteger();
        startServer(MEGABYTE, body -> {
            requests.incrementAndGet();
            return digest(body);
        });

        final var client = connect();
        client.send(head);
        assertEquals(400, client.readResponse().status());
        assertTrue(client.isClosedByServer());
        assertEquals(0, requests.get());
    }

    /**
     * The contrast to streaming: a request declaring its length up front is turned away by HttpObjectAggregator on
     * its headers alone.
     */
    @Test
    void contentLengthOverLimitIsRejectedWith413() throws Exception {
        final var requests = new AtomicInteger();
        startServer(MEGABYTE, body -> {
            requests.incrementAndGet();
            return digest(body);
        });

        final var client = connect();
        client.send("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + (MEGABYTE + 1) + "\r\n\r\n");
        assertEquals(413, client.readResponse().status());
        assertEquals(0, requests.get());
        assertEquals(List.of(), List.copyOf(observed));
    }

    /**
     * One upload running into the body limit takes down its own connection only: uploads in progress on other
     * connections at that very moment complete undisturbed.
     */
    @Test
    void rejectedUploadDoesNotDisturbConcurrentUploads() throws Exception {
        final int uploads = 4;
        final int chunks = MEGABYTE / CHUNK;
        final var firstChunksRead = new CountDownLatch(uploads);
        startServer(MEGABYTE, body -> {
            final var crc = new CRC32();
            final var first = body.readNBytes(CHUNK);
            crc.update(first);
            firstChunksRead.countDown();

            final var bytes = new byte[8192];
            long count = first.length;
            int read;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
            }
            return digestResponse(count, crc);
        });

        record Upload(RawClient client, byte[] block, CRC32 checksum) {
            // A separate payload and checksum for each connection.
        }
        final var uploading = new ArrayList<Upload>();
        for (int i = 0; i < uploads; ++i) {
            final var current = new Upload(connect(), block(200 + i), new CRC32());
            uploading.add(current);
            current.client().send(CHUNKED_HEAD);
            upload(current.client(), current.block(), 0, 1, current.checksum());
        }
        assertTrue(firstChunksRead.await(5, TimeUnit.SECONDS));

        // Keep every other request open while the first one is rejected.
        final var oversized = uploading.removeFirst();
        upload(oversized.client(), oversized.block(), 1, chunks, oversized.checksum());
        oversized.client().send("1\r\nX");
        final var rejected = oversized.client().readResponse();
        assertEquals(413, rejected.status());
        assertEquals("close", rejected.headers().get("connection"));

        for (var current : uploading) {
            upload(current.client(), current.block(), 1, chunks, current.checksum());
            current.client().send(LAST_CHUNK);
            assertEquals(digestText(MEGABYTE, current.checksum()), okBody(current.client().readResponse()));
        }
    }

    /**
     * A client disappearing while megabytes of its upload are queued for a consumer which is not reading: the queued
     * content is released by the disconnect, without waiting for the consumer to come back.
     */
    @Test
    void clientDisconnectReleasesContentQueuedForStalledConsumer() throws Exception {
        final int chunks = 8 * MEGABYTE / CHUNK;
        final var stalled = new CountDownLatch(1);
        final var resume = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        startServer(16 * MEGABYTE, body -> {
            assertEquals(CHUNK, body.readNBytes(CHUNK).length);
            stalled.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));
            try {
                body.read();
                throw new AssertionError("Body of a disconnected client remained readable");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        });

        final var client = connect();
        client.send(CHUNKED_HEAD);
        // the server reads all of this while its consumer is stalled, i.e. it ends up queued
        upload(client, block(7), 0, chunks, new CRC32());
        assertTrue(stalled.await(5, TimeUnit.SECONDS));
        client.close();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertEquals(0, alloc.metric().usedDirectMemory()));
        // only now does the consumer get to look at the body again
        resume.countDown();
        assertNotNull(failure.get(5, TimeUnit.SECONDS));
    }

    private static String okBody(final RawResponse response) {
        assertEquals(200, response.status());
        return response.body();
    }

    private static void assertDigest(final String expectedContent, final RawResponse response) {
        assertEquals(200, response.status());
        assertEquals(digestText(expectedContent), response.body());
    }

    private void startServer(final int maxRequestBodySize, final BodyHandler handler) throws Exception {
        port = TestUtils.freePort();
        startServer(HTTPServerOverTcp.of(localAddress, port), maxRequestBodySize, handler);
    }

    private void startServer(final Transport transport, final int maxRequestBodySize, final BodyHandler handler)
            throws Exception {
        final var limits = HTTPServerLimits.DEFAULT.withMaxRequestBodySize(maxRequestBodySize);
        final var listener = new TransportChannelListener<HTTPTransportChannel>() {
            @Override
            public void onTransportChannelEstablished(final HTTPTransportChannel channel) {
                connections.add(channel.channel());
                channel.channel().pipeline().addLast(new HTTPServerSessionBootstrap(channel.scheme(), limits) {
                    @Override
                    protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
                        return new PipelinedHTTPServerSession(scheme, Uint32.valueOf(CHUNK)) {
                            @Override
                            public void handlerAdded(final ChannelHandlerContext sessionCtx) {
                                super.handlerAdded(sessionCtx);
                                if (authHandlerFactory != null) {
                                    sessionCtx.pipeline().addBefore(sessionCtx.name(), null,
                                        authHandlerFactory.create());
                                }
                            }

                            @Override
                            public void channelRead(final ChannelHandlerContext sessionCtx, final Object msg)
                                    throws Exception {
                                observed.add(msg.getClass());
                                super.channelRead(sessionCtx, msg);
                            }

                            @Override
                            protected PreparedRequest prepareRequest(final ImplementedMethod method,
                                    final URI targetUri, final HttpHeaders headers) {
                                return new TestRequest(handler);
                            }
                        };
                    }

                    @Override
                    protected void configureHttp2(final ChannelHandlerContext ctx) {
                        throw new UnsupportedOperationException("HTTP/2 is not covered by this test");
                    }

                    @Override
                    protected void configureHttp3(final ChannelHandlerContext ctx) {
                        throw new UnsupportedOperationException("HTTP/3 is not covered by this test");
                    }
                });
            }

            @Override
            public void onTransportChannelFailed(final Throwable cause) {
                transportFailures.add(cause);
            }
        };

        server = HTTPServer.listen(listener,
            bootstrapFactory.newServerBootstrap().childOption(ChannelOption.ALLOCATOR, alloc),
            new HttpServerStackConfiguration(transport)).get(5, TimeUnit.SECONDS);
    }

    private RawClient connect() throws IOException {
        return register(new Socket(localAddress, port));
    }

    /**
     * Connect over TLS without offering ALPN, which leaves the server to fall back to HTTP/1.1. The server presents a
     * certificate generated for the test, which is accepted as is.
     */
    private RawClient connectTls() throws Exception {
        final var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                // Nothing to check
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                // Nothing to check
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } }, null);
        final var socket = (SSLSocket) context.getSocketFactory().createSocket(localAddress, port);
        socket.startHandshake();
        return register(socket);
    }

    private RawClient register(final Socket socket) throws IOException {
        final var client = new RawClient(socket);
        clients.add(client);
        return client;
    }

    /**
     * Send chunks {@code from} (inclusive) to {@code to} (exclusive) of an upload, each a differently-stamped copy of
     * {@code block}, updating {@code crc} with what was sent.
     */
    private static void upload(final RawClient client, final byte[] block, final int from, final int to,
            final CRC32 crc) throws IOException {
        for (int i = from; i < to; ++i) {
            stamp(block, i);
            crc.update(block);
            client.sendChunk(block);
        }
    }

    private static byte[] block(final int seed) {
        final var block = new byte[CHUNK];
        new Random(seed).nextBytes(block);
        return block;
    }

    // Make every block of an upload different, so that reordered or repeated content cannot go unnoticed
    private static void stamp(final byte[] block, final int index) {
        block[0] = (byte) (index >>> 8);
        block[1] = (byte) index;
    }

    private static Response digest(final @Nullable InputStream body) throws IOException {
        final var crc = new CRC32();
        long count = 0;
        if (body != null) {
            final var bytes = new byte[8192];
            int read;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
            }
        }
        return digestResponse(count, crc);
    }

    private static Response digestResponse(final long count, final CRC32 crc) {
        return ByteBufResponse.ok(Unpooled.copiedBuffer(digestText(count, crc), StandardCharsets.US_ASCII));
    }

    private static String digestText(final String content) {
        final var crc = new CRC32();
        crc.update(content.getBytes(StandardCharsets.US_ASCII));
        return digestText(content.length(), crc);
    }

    private static String digestText(final long count, final CRC32 crc) {
        return "bytes=" + count + " crc=" + Long.toHexString(crc.getValue());
    }
}
