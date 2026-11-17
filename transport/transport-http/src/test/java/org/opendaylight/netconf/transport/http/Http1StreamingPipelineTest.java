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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Verification of HTTP/1.1 request streaming on the pipeline {@link HTTPScheme#HTTP} installs in production: requests
 * arrive as wire bytes, pass through the real codec, keep-alive handler, {@link Http1RequestDispatcher} and
 * {@link io.netty.handler.codec.http.HttpObjectAggregator}, and are consumed either directly or by a
 * {@link PipelinedHTTPServerSession} running the body consumer on its request executor.
 *
 * <p>Every buffer entering or created by the pipeline comes from a dedicated allocator which accounts for the memory
 * it has handed out. Each test ends by asserting that accounting is back at zero, i.e. that every {@link ByteBuf} has
 * been released whatever the outcome of the request was.
 *
 * <p>Bodies are read with blocking calls, some of them on the test thread itself. A regression which loses content or
 * an end-of-body signal would leave such a read waiting forever, hence every test runs under a deadline: its expiry
 * interrupts the reader and fails the test instead of hanging the build.
 */
@Timeout(10)
class Http1StreamingPipelineTest {
    /**
     * The request-phase work of a {@link PendingRequest}: consume the body and produce a response, or throw to signal
     * a failure of whatever is parsing the body.
     */
    @FunctionalInterface
    private interface BodyHandler {
        Response handle(@Nullable InputStream body) throws Exception;
    }

    /**
     * Records what makes it past the aggregator, which is where the authentication handler and the session sit. When
     * {@link #terminal}, it also takes ownership of those messages instead of passing them on.
     */
    private static final class Probe extends ChannelInboundHandlerAdapter {
        private final List<Class<?>> observed = new ArrayList<>();
        private final List<Object> messages = new ArrayList<>();
        private boolean terminal;

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            observed.add(msg.getClass());
            if (terminal) {
                messages.add(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        long fullRequests() {
            return observed.stream().filter(FullHttpRequest.class::isAssignableFrom).count();
        }

        long streamingRequests() {
            return observed.stream().filter(StreamingHttpRequest.class::equals).count();
        }
    }

    private final class TestRequest extends PendingRequest<Object> {
        private final BodyHandler handler;

        TestRequest(final BodyHandler handler) {
            this.handler = handler;
        }

        @Override
        @SuppressWarnings("checkstyle:illegalCatch")
        public void execute(final PendingRequestListener listener, final InputStream body) {
            // The outcome is handed back to the test thread, which doubles as the EmbeddedChannel's event loop: that
            // keeps every interaction with the channel on a single thread.
            try {
                final var response = handler.handle(body);
                completions.add(() -> listener.requestComplete(this, response));
            } catch (Exception e) {
                completions.add(() -> listener.requestFailed(this, e));
            } finally {
                closeAggregated(body);
            }
        }

        @Override
        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper;
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
    // 104 MiB, i.e. comfortably more than 100 MB whichever way that is counted
    private static final int LARGE_PAYLOAD = 104 * 1024 * 1024;

    private final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(false);
    private final BlockingQueue<Runnable> completions = new LinkedBlockingQueue<>();
    private final AtomicInteger prepared = new AtomicInteger();
    private final Probe probe = new Probe();

    private EmbeddedChannel channel;

    @AfterEach
    void afterEach() {
        probe.messages.forEach(ReferenceCountUtil::release);
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
        // ByteBuf lifecycle: whatever happened to the request, nothing the pipeline allocated or received is retained
        assertEquals(0, alloc.metric().usedHeapMemory(), "leaked heap buffers");
        assertEquals(0, alloc.metric().usedDirectMemory(), "leaked direct buffers");
    }

    @Test
    void chunkedRequestIsStreamedWithoutFullHttpRequest() throws Exception {
        newChannel(1024);
        probe.terminal = true;

        channel.writeInbound(ascii(CHUNKED_HEAD));
        // the headers alone make it past the aggregator
        assertEquals(List.of(StreamingHttpRequest.class), probe.observed);
        final var body = assertInstanceOf(StreamingHttpRequest.class, probe.messages.getFirst()).body();
        assertEquals(0, body.available());

        // content is readable as soon as it is decoded, well before the request is complete
        channel.writeInbound(ascii("5\r\nfirst\r\n"));
        assertEquals("first", new String(body.readNBytes(5), CharsetUtil.US_ASCII));
        channel.writeInbound(ascii("6\r\nsecond\r\n"));
        assertEquals("second", new String(body.readNBytes(6), CharsetUtil.US_ASCII));
        assertEquals(0, body.available());

        channel.writeInbound(ascii("4\r\nlast\r\n" + LAST_CHUNK));
        assertEquals("last", new String(body.readAllBytes(), CharsetUtil.US_ASCII));

        assertEquals(List.of(StreamingHttpRequest.class), probe.observed);
        assertEquals(0, probe.fullRequests());
        assertNull(channel.readOutbound());
        assertTrue(channel.isActive());
    }

    @Test
    void contentLengthRequestIsAggregated() {
        newChannel(1024);
        probe.terminal = true;

        channel.writeInbound(ascii("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Content-Length: 11\r
            \r
            first"""));
        // HttpObjectAggregator holds on to the request until its body is complete
        assertEquals(List.of(), probe.observed);

        channel.writeInbound(ascii("second"));
        assertEquals(1, probe.observed.size());
        assertEquals(0, probe.streamingRequests());
        final var request = assertInstanceOf(FullHttpRequest.class, probe.messages.getFirst());
        assertEquals("firstsecond", request.content().toString(CharsetUtil.US_ASCII));
        assertNull(channel.readOutbound());
    }

    @Test
    void contentLengthRequestOverLimitIsRejectedByAggregator() {
        newChannel(1024, session(Http1StreamingPipelineTest::digest));

        channel.writeInbound(ascii("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Content-Length: 1025\r
            \r
            """));
        assertStatus(413, readWire());
        assertEquals(List.of(), probe.observed);
        assertEquals(0, prepared.get());
    }

    /**
     * A body of more than 100 MB flows through the pipeline one chunk at a time: at no point is more than the chunk
     * being delivered queued for the consumer or held by the allocator, and no {@link FullHttpRequest} is created.
     */
    @Test
    void payloadOver100MbIsStreamedIncrementally() throws Exception {
        newChannel(128 * 1024 * 1024);
        probe.terminal = true;
        channel.writeInbound(ascii(CHUNKED_HEAD));
        final var body = assertInstanceOf(StreamingHttpRequest.class, probe.messages.getFirst()).body();

        final var data = new byte[CHUNK];
        new Random(42).nextBytes(data);
        final var sink = new byte[CHUNK];
        final var sent = new CRC32();
        final var received = new CRC32();
        long total = 0;
        long peakMemory = 0;
        int peakQueued = 0;

        for (int i = 0; i < LARGE_PAYLOAD / CHUNK; ++i) {
            // make every chunk different, so that reordered or repeated content cannot go unnoticed
            data[0] = (byte) (i >>> 8);
            data[1] = (byte) i;
            sent.update(data);

            channel.writeInbound(chunk(data, 0, CHUNK));
            peakQueued = Math.max(peakQueued, body.available());
            peakMemory = Math.max(peakMemory, alloc.metric().usedHeapMemory());

            assertEquals(CHUNK, body.readNBytes(sink, 0, CHUNK));
            received.update(sink);
            total += CHUNK;
            assertEquals(0, body.available());
        }
        channel.writeInbound(ascii(LAST_CHUNK));
        assertEquals(-1, body.read());

        assertEquals(LARGE_PAYLOAD, total);
        assertTrue(total > 100_000_000);
        assertEquals(sent.getValue(), received.getValue());
        assertEquals(CHUNK, peakQueued);
        assertTrue(peakMemory < 2L * CHUNK, "pipeline held " + peakMemory + " bytes of a streamed request");
        assertEquals(List.of(StreamingHttpRequest.class), probe.observed);
        assertNull(channel.readOutbound());
        assertTrue(channel.isActive());
    }

    /**
     * A consumer which is slower than the transport first waits for content which has not arrived yet, then falls
     * behind: the producer is never held up, everything it delivers is queued, and the consumer still sees every byte
     * in order.
     */
    @Test
    void slowConsumerNeitherBlocksTransportNorLosesData() throws Exception {
        final int chunkSize = 16 * 1024;
        final int chunks = 32;
        final var consumer = new AtomicReference<Thread>();
        final var firstChunkRead = new CountDownLatch(1);
        final var resume = new CountDownLatch(1);
        newChannel(1024 * 1024, session(body -> {
            consumer.set(Thread.currentThread());
            final var crc = new CRC32();
            final var first = body.readNBytes(chunkSize);
            crc.update(first);
            long count = first.length;
            firstChunkRead.countDown();
            assertTrue(resume.await(5, TimeUnit.SECONDS));

            // drain in small, odd-sized reads which straddle the boundaries of the queued buffers
            final var bytes = new byte[97];
            int read;
            while ((read = body.read(bytes)) != -1) {
                crc.update(bytes, 0, read);
                count += read;
            }
            return digestResponse(count, crc);
        }));

        final var data = new byte[chunkSize];
        new Random(7).nextBytes(data);
        final var sent = new CRC32();

        // the consumer starts on the headers alone and parks waiting for content
        channel.writeInbound(ascii(CHUNKED_HEAD));
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            final var thread = consumer.get();
            return thread != null && thread.getState() == Thread.State.WAITING;
        });
        assertTrue(Thread.currentThread() != consumer.get() && consumer.get().isVirtual());

        sent.update(data);
        channel.writeInbound(chunk(data, 0, chunkSize));
        assertTrue(firstChunkRead.await(5, TimeUnit.SECONDS));

        // the consumer is now stalled: the transport keeps delivering regardless
        for (int i = 1; i < chunks; ++i) {
            data[0] = (byte) i;
            sent.update(data);
            channel.writeInbound(chunk(data, 0, chunkSize));
        }
        channel.writeInbound(ascii(LAST_CHUNK));
        assertTrue(alloc.metric().usedHeapMemory() >= (chunks - 1) * (long) chunkSize);
        assertNull(channel.readOutbound());

        resume.countDown();
        completeRequest();
        final var response = readWire();
        assertStatus(200, response);
        assertTrue(response.endsWith(digestText((long) chunks * chunkSize, sent)), response);
        assertEquals(0, alloc.metric().usedHeapMemory());
        assertEquals(0, probe.fullRequests());
    }

    @Test
    void chunkedBodyAtLimitIsAccepted() throws Exception {
        newChannel(1024, session(Http1StreamingPipelineTest::digest));
        final var data = new byte[256];

        channel.writeInbound(ascii(CHUNKED_HEAD));
        for (int i = 0; i < 4; ++i) {
            channel.writeInbound(chunk(data, 0, data.length));
        }
        channel.writeInbound(ascii(LAST_CHUNK));

        completeRequest();
        final var response = readWire();
        assertStatus(200, response);
        assertTrue(response.contains("bytes=1024 "), response);
        assertTrue(channel.isActive());
    }

    /**
     * The limit HttpObjectAggregator would apply covers streamed requests too: the first content byte beyond it is
     * answered 413, the consumer is failed rather than handed a truncated body, and the connection is closed with
     * everything queued or still arriving released.
     */
    @Test
    void chunkedBodyOverLimitIsRejectedWith413() throws Exception {
        final var consumedLimit = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        newChannel(1024, session(body -> {
            assertEquals(1024, body.readNBytes(1024).length);
            consumedLimit.countDown();
            try {
                body.read();
                throw new AssertionError("Content beyond the limit reached the consumer");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        }));

        channel.writeInbound(ascii(CHUNKED_HEAD));
        channel.writeInbound(chunk(new byte[1024], 0, 1024));
        assertTrue(consumedLimit.await(5, TimeUnit.SECONDS));
        assertNull(channel.readOutbound());

        // one byte too many, followed by content which arrives in the same read and must not be retained either
        final var excess = ascii("1\r\nX\r\n5\r\nhello\r\n" + LAST_CHUNK);
        channel.writeInbound(excess);
        assertEquals(0, excess.refCnt());

        final var response = readWire();
        assertStatus(413, response);
        assertTrue(response.contains("connection: close"), response);
        assertFalse(channel.isActive());
        assertNotNull(failure.get(5, TimeUnit.SECONDS));

        // the consumer's own failure report must not produce a second response
        completeRequest();
        assertEquals("", readWire());
    }

    @ParameterizedTest
    @ValueSource(strings = { "zz\r\n", "FFFFFFFFFFFFFFFFF\r\n" })
    void malformedChunkIsRejectedWith400(final String garbage) throws Exception {
        final var consumed = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        newChannel(1024, session(body -> {
            assertEquals("abc", new String(body.readNBytes(3), CharsetUtil.US_ASCII));
            consumed.countDown();
            try {
                body.read();
                throw new AssertionError("Malformed content reached the consumer");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        }));

        channel.writeInbound(ascii(CHUNKED_HEAD + "3\r\nabc\r\n"));
        assertTrue(consumed.await(5, TimeUnit.SECONDS));

        final var malformed = ascii(garbage);
        channel.writeInbound(malformed);
        assertEquals(0, malformed.refCnt());

        assertStatus(400, readWire());
        assertFalse(channel.isActive());
        assertNotNull(failure.get(5, TimeUnit.SECONDS));
        completeRequest();
        assertEquals("", readWire());
    }

    @ParameterizedTest
    @MethodSource
    void malformedFramingNeverReachesConsumer(final String framing, final int status) {
        newChannel(1024, session(Http1StreamingPipelineTest::digest));

        final var request = ascii("POST /upload HTTP/1.1\r\nHost: localhost\r\n" + framing + "\r\n\r\n3\r\nabc\r\n"
            + LAST_CHUNK);
        channel.writeInbound(request);
        assertEquals(0, request.refCnt());

        assertStatus(status, readWire());
        assertEquals(0, prepared.get());
        assertEquals(0, probe.streamingRequests());
    }

    private static Stream<Arguments> malformedFramingNeverReachesConsumer() {
        return Stream.of(
            Arguments.of("Transfer-Encoding: chunked, chunked", 400),
            Arguments.of("Transfer-Encoding: chunked, gzip", 400),
            Arguments.of("Transfer-Encoding: gzip", 400),
            Arguments.of("Transfer-Encoding: chunked\r\nContent-Length: 3", 400),
            Arguments.of("Transfer-Encoding: gzip, chunked", 501));
    }

    /**
     * A client going away mid-request releases the content its consumer has not read yet, and fails the body rather
     * than letting the consumer mistake what had arrived for a complete request. There is nobody left to respond to.
     */
    @Test
    void clientDisconnectReleasesUnreadContent() throws Exception {
        final var firstRead = new CountDownLatch(1);
        final var disconnected = new CountDownLatch(1);
        final var failure = new CompletableFuture<IOException>();
        newChannel(1024, session(body -> {
            assertEquals('a', body.read());
            firstRead.countDown();
            // busy with what it has read: "bc" stays queued
            assertTrue(disconnected.await(5, TimeUnit.SECONDS));
            try {
                body.read();
                throw new AssertionError("Body of a disconnected client remained readable");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        }));

        channel.writeInbound(ascii(CHUNKED_HEAD + "3\r\nabc\r\n"));
        assertTrue(firstRead.await(5, TimeUnit.SECONDS));
        assertTrue(alloc.metric().usedHeapMemory() > 0);

        channel.close();
        // the unread content is released by the disconnect itself, not by the consumer getting around to it
        assertEquals(0, alloc.metric().usedHeapMemory());
        disconnected.countDown();

        assertNotNull(failure.get(5, TimeUnit.SECONDS));
        completeRequest();
        assertEquals("", readWire());
    }

    /**
     * A consumer waiting for more content, rather than busy with what it has, is woken by the disconnect as well.
     */
    @Test
    void clientDisconnectWakesBlockedConsumer() throws Exception {
        final var consumer = new AtomicReference<Thread>();
        final var failure = new CompletableFuture<IOException>();
        newChannel(1024, session(body -> {
            assertEquals("abc", new String(body.readNBytes(3), CharsetUtil.US_ASCII));
            consumer.set(Thread.currentThread());
            try {
                body.read();
                throw new AssertionError("Body of a disconnected client completed");
            } catch (IOException e) {
                failure.complete(e);
                throw e;
            }
        }));

        channel.writeInbound(ascii(CHUNKED_HEAD + "3\r\nabc\r\n"));
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            final var thread = consumer.get();
            return thread != null && thread.getState() == Thread.State.WAITING;
        });

        channel.close();
        assertNotNull(failure.get(5, TimeUnit.SECONDS));
        completeRequest();
        assertEquals("", readWire());
    }

    /**
     * A consumer giving up on a body it cannot parse is answered straight away. The remainder of that body is
     * discarded as it arrives and the connection goes on to serve the next request.
     */
    @Test
    void parserFailureDiscardsRemainingBodyAndKeepsConnection() throws Exception {
        final var requests = new AtomicInteger();
        newChannel(1024, session(body -> {
            if (requests.getAndIncrement() == 0) {
                assertEquals("{x!", new String(body.readNBytes(3), CharsetUtil.US_ASCII));
                throw new IllegalArgumentException("Malformed document");
            }
            return digest(body);
        }));

        channel.writeInbound(ascii(CHUNKED_HEAD + "3\r\n{x!\r\n"));
        completeRequest();
        final var failed = readWire();
        assertStatus(500, failed);
        assertTrue(failed.endsWith("Malformed document"), failed);
        assertTrue(channel.isActive());

        // the client is still sending the request which has already been answered
        final var remainder = ascii("8\r\n ignored\r\n8\r\n content\r\n" + LAST_CHUNK);
        channel.writeInbound(remainder);
        assertEquals(0, remainder.refCnt());
        assertEquals(0, alloc.metric().usedHeapMemory());
        assertEquals("", readWire());

        channel.writeInbound(ascii(CHUNKED_HEAD + "2\r\nok\r\n" + LAST_CHUNK));
        completeRequest();
        final var response = readWire();
        assertStatus(200, response);
        assertTrue(response.contains("bytes=2 "), response);
        assertTrue(channel.isActive());
        assertEquals(2, prepared.get());
    }

    /**
     * The dispatcher decides afresh for every request of a persistent connection, and a streamed request honours
     * {@code Connection: close} just like an aggregated one.
     */
    @Test
    void keepAliveServesStreamedAndAggregatedRequestsInTurn() throws Exception {
        newChannel(1024, session(Http1StreamingPipelineTest::digest));

        channel.writeInbound(ascii(CHUNKED_HEAD + "3\r\nabc\r\n2\r\nde\r\n" + LAST_CHUNK));
        assertDigest(5, "abcde");

        channel.writeInbound(ascii("""
            PUT /upload HTTP/1.1\r
            Host: localhost\r
            Content-Length: 4\r
            \r
            body"""));
        assertDigest(4, "body");

        // a chunked request need not have a body at all
        channel.writeInbound(ascii(CHUNKED_HEAD + LAST_CHUNK));
        assertDigest(0, "");

        channel.writeInbound(ascii("GET /upload HTTP/1.1\r\nHost: localhost\r\n\r\n"));
        assertDigest(0, "");
        assertTrue(channel.isActive());

        channel.writeInbound(ascii("""
            POST /upload HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            Transfer-Encoding: chunked\r
            \r
            4\r
            done\r
            """ + LAST_CHUNK));
        completeRequest();
        final var last = readWire();
        assertStatus(200, last);
        assertTrue(last.contains("connection: close"), last);
        assertFalse(channel.isActive());

        assertEquals(5, prepared.get());
        assertEquals(3, probe.streamingRequests());
        assertEquals(2, probe.fullRequests());
    }

    private void assertDigest(final long expectedBytes, final String expectedContent) throws Exception {
        completeRequest();
        final var response = readWire();
        assertStatus(200, response);
        final var crc = new CRC32();
        crc.update(expectedContent.getBytes(CharsetUtil.US_ASCII));
        assertTrue(response.endsWith(digestText(expectedBytes, crc)), response);
        assertTrue(channel.isActive());
    }

    private void newChannel(final int maxRequestBodySize, final ChannelHandler... tail) {
        final var anchor = new ChannelInboundHandlerAdapter();
        channel = new EmbeddedChannel();
        channel.config().setAllocator(alloc);
        channel.pipeline().addLast(anchor).addLast(probe).addLast(tail);
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(anchor),
            HTTPServerLimits.DEFAULT.withMaxRequestBodySize(maxRequestBodySize));
    }

    private PipelinedHTTPServerSession session(final BodyHandler handler) {
        return new PipelinedHTTPServerSession(HTTPScheme.HTTP, Uint32.valueOf(CHUNK)) {
            @Override
            protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
                    final HttpHeaders headers) {
                prepared.incrementAndGet();
                return new TestRequest(handler);
            }
        };
    }

    /**
     * Wait for the consumer to finish and report its outcome to the session, as the request executor would.
     */
    private void completeRequest() throws InterruptedException {
        final var completion = completions.poll(5, TimeUnit.SECONDS);
        assertNotNull(completion, "request consumer did not finish");
        completion.run();
        channel.runPendingTasks();
    }

    private ByteBuf ascii(final String str) {
        return ByteBufUtil.writeAscii(alloc, str);
    }

    private ByteBuf chunk(final byte[] bytes, final int offset, final int length) {
        final var header = Integer.toHexString(length) + "\r\n";
        final var buf = alloc.buffer(header.length() + length + 2, header.length() + length + 2);
        buf.writeCharSequence(header, CharsetUtil.US_ASCII);
        buf.writeBytes(bytes, offset, length);
        buf.writeByte('\r').writeByte('\n');
        return buf;
    }

    private String readWire() {
        final var sb = new StringBuilder();
        ByteBuf bytes;
        while ((bytes = channel.readOutbound()) != null) {
            sb.append(bytes.toString(CharsetUtil.US_ASCII));
            bytes.release();
        }
        return sb.toString();
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

    private static void assertStatus(final int expected, final String response) {
        assertTrue(response.startsWith("HTTP/1.1 " + expected + " "), response);
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
        return ByteBufResponse.ok(Unpooled.copiedBuffer(digestText(count, crc), CharsetUtil.US_ASCII));
    }

    private static String digestText(final long count, final CRC32 crc) {
        return "bytes=" + count + " crc=" + Long.toHexString(crc.getValue());
    }
}
