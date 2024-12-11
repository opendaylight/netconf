/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.opendaylight.restconf.server.PathParameters.MODULES;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.DataGetResult;

class ErrorHandlerTest extends AbstractRequestProcessorTest {
    private static final String OPERATIONS_PATH = BASE_PATH + OPERATIONS;
    private static final String OPERATIONS_WITH_ID_PATH = BASE_PATH + OPERATIONS + "/" + ID_PATH;
    private static final String CONTENT = "content";

    @Mock
    private FormattableBody body;


    @ParameterizedTest
    @MethodSource
    void notFoundRequest(final HttpMethod method, final String uri) {
        final var response = dispatch(buildRequest(method, uri, DEFAULT_ENCODING, CONTENT));
        assertResponse(response, HttpResponseStatus.NOT_FOUND);
    }

    private static Stream<Arguments> notFoundRequest() {
        return Stream.of(
            // {+restconf}, see the corresponding FIXME
            Arguments.of(HttpMethod.GET, BASE_PATH),
            Arguments.of(HttpMethod.GET, BASE_PATH + "/test"));
    }

    @ParameterizedTest
    @MethodSource
    void methodNotAllowed(final TestEncoding encoding, final HttpMethod method, final String uri) {
        final var response = dispatch(buildRequest(method, uri, encoding, CONTENT));
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, response.status());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    }

    private static Stream<Arguments> methodNotAllowed() {
        return Stream.of(
            // valid URI, unsupported HTTP method (1 per URI used)
            Arguments.of(TestEncoding.XML, HttpMethod.PUT, OPERATIONS_PATH),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + YANG_LIBRARY_VERSION),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + MODULES)
        );
    }

    @ParameterizedTest
    @MethodSource
    void unsupportedMediaType(final TestEncoding encoding, final HttpMethod method, final String uri) {
        final var request = buildRequest(method, uri, encoding, CONTENT);
        request.headers().remove(HttpHeaderNames.CONTENT_TYPE);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ACCEPT, """
            application/yang-data+json, \
            application/yang-data+xml, \
            application/json, \
            application/xml, \
            text/xml"""));
    }

    private static Stream<Arguments> unsupportedMediaType() {
        return Stream.of(
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DATA_PATH),
            Arguments.of(TestEncoding.JSON, HttpMethod.PUT, DATA_PATH),
            Arguments.of(TestEncoding.JSON, HttpMethod.POST, OPERATIONS_WITH_ID_PATH)
        );
    }

    @Test
    void unsupportedMediaTypePatch() {
        final var request = buildRequest(HttpMethod.PATCH, DATA_PATH, TestEncoding.XML, CONTENT);
        request.headers().remove(HttpHeaderNames.CONTENT_TYPE);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ACCEPT, """
            application/yang-data+json, \
            application/yang-data+xml, \
            application/yang-patch+json, \
            application/yang-patch+xml, \
            application/json, \
            application/xml, \
            text/xml"""));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void runtimeException(final TestEncoding encoding) {
        final var errorMessage = "runtime-error";
        doThrow(new IllegalStateException(errorMessage)).when(server).dataGET(any());
        final var request = buildRequest(HttpMethod.GET, DATA_PATH, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("runtime-error", response.content().toString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void apiPathParseFailure(final TestEncoding encoding) {
        final var request = buildRequest(HttpMethod.GET, DATA_PATH + "/-invalid", encoding, null);
        final var response = dispatch(request);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        assertEquals("Bad request path '-invalid': 'Expecting [a-zA-Z_], not '-''",
            response.content().toString(StandardCharsets.UTF_8));
    }

    @Disabled("Will be disabled until NETCONF-1492 has been resolved")
    @ParameterizedTest
    @MethodSource("encodings")
    void encodingResponseFailure(final TestEncoding encoding) throws IOException {
        final var result = new DataGetResult(body);
        doAnswer(answerCompleteWith(result)).when(server).dataGET(any());

        final var errorMessage = "encoding-error";
        final var exception = new IOException(errorMessage);
        if (encoding.isJson()) {
            doThrow(exception).when(body).formatToJSON(any(), any());
        } else {
            doThrow(exception).when(body).formatToXML(any(), any());
        }

        final var request = buildRequest(HttpMethod.GET, DATA_PATH, encoding, null);
        final var response = dispatchWithAlloc(request);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("encoding-error", response.content().toString(StandardCharsets.UTF_8));
    }
}
