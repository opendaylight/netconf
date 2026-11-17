/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * RESTCONF requests arriving over HTTP/1.1 with {@code Transfer-Encoding: chunked}, i.e. those whose body is handed
 * to the RESTCONF parsers as it arrives rather than as an aggregated buffer.
 */
@Timeout(20)
class StreamingRequestE2ETest extends AbstractE2ETest {
    private static final String PARENT_URI = "/rests/data/example-jukebox:jukebox/library";
    private static final String ITEM_URI = PARENT_URI + "/artist=art%2Fist";
    private static final String ARTIST_JSON = """
        {
            "example-jukebox:artist": [
                {
                    "name": "art/ist",
                    "album": [{
                        "name": "album1",
                        "genre": "example-jukebox:rock",
                        "year": 2020
                    }]
                }
            ]
        }""";
    // Small enough to have chunk boundaries fall inside names and values
    private static final int CHUNK_SIZE = 7;
    // Deadline for a single request: a response which never arrives fails the test instead of hanging it
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        // ensure the parent exists before we insert a child node
        final var response = invokeRequest(HttpMethod.PUT, "/rests/data/example-jukebox:jukebox", APPLICATION_JSON, """
            {
                "example-jukebox:jukebox": {
                    "library": {}
                }
            }""");
        final var status = response.status().code();
        assertTrue(status == HttpResponseStatus.OK.code() || status == HttpResponseStatus.CREATED.code());
    }

    @Test
    void chunkedRequestIsParsedAndApplied() throws Exception {
        try (var client = newClient()) {
            final var response = postChunked(client, ARTIST_JSON);
            assertEquals(201, response.statusCode());
        }
        assertContentJson(ITEM_URI, ARTIST_JSON);
    }

    /**
     * A document the parser gives up on part-way through is reported as a RESTCONF error, applies nothing, and does
     * not prevent further requests.
     */
    @Test
    void malformedChunkedRequestIsRejected() throws Exception {
        try (var client = newClient()) {
            final var malformed = ARTIST_JSON.replace("\"album\": [{", "\"album\": [{ !!! ");
            final var response = postChunked(client, malformed);
            assertEquals(400, response.statusCode());
            final var error = new JSONObject(response.body()).getJSONObject("errors").getJSONArray("error")
                .getJSONObject(0);
            assertEquals(ErrorTag.MALFORMED_MESSAGE.elementBody(), error.getString("error-tag"));
            assertErrorResponseJson(invokeRequest(HttpMethod.GET, ITEM_URI), ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);

            assertEquals(201, postChunked(client, ARTIST_JSON).statusCode());
        }
        assertContentJson(ITEM_URI, ARTIST_JSON);
    }

    /**
     * Disconnect while the parser is waiting for the closing brace of the JSON document.
     * Wait for this request's parser to block and its thread to exit before checking the datastore.
     */
    @Test
    void disconnectedChunkedRequestIsNotApplied(@TempDir final Path tempDir) throws Exception {
        final var bytes = ARTIST_JSON.substring(0, ARTIST_JSON.lastIndexOf('}')).getBytes(StandardCharsets.UTF_8);
        final String requestThreadSuffix;
        try (var socket = new Socket(localAddress(), port())) {
            // The server names request threads after the peer address and the request number on that connection.
            requestThreadSuffix = ":" + socket.getLocalPort() + "-http-server-req-0\"";
            final var out = socket.getOutputStream();
            out.write("""
                POST %s HTTP/1.1\r
                Host: %s:%d\r
                Authorization: %s\r
                Content-Type: application/json\r
                Transfer-Encoding: chunked\r
                \r
                %x\r
                """.formatted(PARENT_URI, localAddress(), port(), authorization(), bytes.length)
                .getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            await("parser waiting for the rest of the JSON document").atMost(Duration.ofSeconds(5))
                .until(() -> Arrays.stream(dumpThreads(tempDir).split("\\R\\R"))
                    .anyMatch(thread -> thread.contains(requestThreadSuffix)
                        && thread.contains("RequestBodyInputStream.read")));
        }

        // the disconnect has terminated the request: nothing is left executing which could still write
        await().atMost(Duration.ofSeconds(5)).until(() -> !dumpThreads(tempDir).contains(requestThreadSuffix));

        assertErrorResponseJson(invokeRequest(HttpMethod.GET, ITEM_URI), ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        // the server carries on, and the same document is accepted once it is sent in full
        try (var client = newClient()) {
            assertEquals(201, postChunked(client, ARTIST_JSON).statusCode());
        }
        assertContentJson(ITEM_URI, ARTIST_JSON);
    }

    /**
     * {@return a dump of all threads, including the virtual threads HTTP requests are executed on}
     */
    private static String dumpThreads(final Path tempDir) throws IOException {
        final var file = Files.createTempFile(tempDir, "threads", ".txt");
        Files.delete(file);
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
            .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
        try {
            return Files.readString(file);
        } finally {
            Files.delete(file);
        }
    }

    private static HttpClient newClient() {
        // HTTP/1.1 without an upgrade attempt: a body of unknown length is then sent chunked
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    private HttpResponse<String> postChunked(final HttpClient client, final String content) throws Exception {
        final var request = HttpRequest.newBuilder(URI.create("http://%s:%d%s".formatted(localAddress(), port(),
                PARENT_URI)))
            .header("Authorization", authorization())
            .header("Content-Type", APPLICATION_JSON)
            .header("Accept", APPLICATION_JSON)
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> fragmented(content)))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * {@return a stream which yields the content a few bytes per read, each of which the client sends as a chunk}
     */
    private static InputStream fragmented(final String content) {
        final var bytes = content.getBytes(StandardCharsets.UTF_8);
        final var fragments = new ArrayList<InputStream>();
        for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
            fragments.add(new ByteArrayInputStream(bytes, offset, Math.min(CHUNK_SIZE, bytes.length - offset)));
        }
        return new SequenceInputStream(Collections.enumeration(fragments));
    }

    private static String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
            (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }
}
