/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.opendaylight.restconf.server.PathParameters.DISCOVERY_BASE;
import static org.opendaylight.restconf.server.PathParameters.HOST_META;
import static org.opendaylight.restconf.server.PathParameters.HOST_META_JSON;
import static org.opendaylight.restconf.server.PathParameters.MODULES;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.PathParameters.YANG_LIBRARY_VERSION;
import static org.opendaylight.restconf.server.ResponseUtils.ENCODING_RESPONSE_ERROR;
import static org.opendaylight.restconf.server.ResponseUtils.UNMAPPED_REQUEST_ERROR;
import static org.opendaylight.restconf.server.ResponseUtils.UNSUPPORTED_MEDIA_TYPE_ERROR;
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertErrorContent;
import static org.opendaylight.restconf.server.TestUtils.assertErrorResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.yangtools.yang.common.ErrorTag;

class ErrorHandlerTest extends AbstractRequestProcessorTest {
    private static final String OPERATIONS_PATH = BASE_PATH + OPERATIONS;
    private static final String OPERATIONS_WITH_ID_PATH = BASE_PATH + OPERATIONS + "/" + ID_PATH;
    private static final String CONTENT = "content";

    @Mock
    private FormattableBody body;

    @ParameterizedTest
    @MethodSource
    void unmappedRequest(final TestEncoding encoding, final HttpMethod method, final String uri) {
        final var request = buildRequest(method, uri, encoding, CONTENT);
        final var response = dispatch(request);
        assertErrorResponse(response, encoding, ErrorTag.DATA_MISSING, UNMAPPED_REQUEST_ERROR);
    }

    private static Stream<Arguments> unmappedRequest() {
        return Stream.of(
            // no processor matching api resource
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, "/"),
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, BASE_PATH),
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, BASE_PATH + "/test"),
            // valid URI, unsupported HTTP method (1 per URI used)
            Arguments.of(TestEncoding.XML, HttpMethod.PUT, OPERATIONS_PATH),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + YANG_LIBRARY_VERSION),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + MODULES),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DISCOVERY_BASE + HOST_META),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DISCOVERY_BASE + HOST_META_JSON)
        );
    }

    @ParameterizedTest
    @MethodSource
    void unsupportedMediaType(final TestEncoding encoding, final HttpMethod method, final String uri) {
        final var request = buildRequest(method, uri, encoding, CONTENT);
        request.headers().remove(HttpHeaderNames.CONTENT_TYPE);
        final var response = dispatch(request);
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertResponse(response, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
        assertErrorContent(content, encoding, ErrorTag.INVALID_VALUE, UNSUPPORTED_MEDIA_TYPE_ERROR);
    }

    private static Stream<Arguments> unsupportedMediaType() {
        return Stream.of(
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DATA_PATH),
            Arguments.of(TestEncoding.JSON, HttpMethod.PUT, DATA_PATH),
            Arguments.of(TestEncoding.XML, HttpMethod.PATCH, DATA_PATH),
            Arguments.of(TestEncoding.JSON, HttpMethod.POST, OPERATIONS_WITH_ID_PATH)
        );
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void runtimeException(final TestEncoding encoding) {
        final var errorMessage = "runtime-error";
        doThrow(new IllegalStateException(errorMessage)).when(service).dataGET(any());
        final var request = buildRequest(HttpMethod.GET, DATA_PATH, encoding, null);
        final var response = dispatch(request);
        assertErrorResponse(response, encoding, ErrorTag.OPERATION_FAILED, errorMessage);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void apiPathParseFailure(final TestEncoding encoding) {
        final var request = buildRequest(HttpMethod.GET, DATA_PATH + "/-invalid", encoding, null);
        final var response = dispatch(request);
        assertErrorResponse(response, encoding, ErrorTag.BAD_ELEMENT, "API Path");
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void encodingResponseFailure(final TestEncoding encoding) throws IOException {
        final var result = new DataGetResult(body);
        doAnswer(answerCompleteWith(result)).when(service).dataGET(any());

        final var errorMessage = "encoding-error";
        final var exception = new IOException(errorMessage);
        if (encoding.isJson()) {
            doThrow(exception).when(body).formatToJSON(any(), any());
        } else {
            doThrow(exception).when(body).formatToXML(any(), any());
        }

        final var request = buildRequest(HttpMethod.GET, DATA_PATH, encoding, null);
        final var response = dispatch(request);
        assertErrorResponse(response, encoding, ErrorTag.OPERATION_FAILED, ENCODING_RESPONSE_ERROR + errorMessage);
    }
}
