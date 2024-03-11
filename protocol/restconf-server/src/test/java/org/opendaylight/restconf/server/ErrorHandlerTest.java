/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
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
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertErrorResponse;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
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

    @Mock
    private FormattableBody body;

    @ParameterizedTest
    @MethodSource
    void unmappedRequest(final TestEncoding encoding, final HttpMethod method, final String uri,
            final AsciiString contentType) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        if (encoding != DEFAULT_ENCODING) {
            request.headers().set(HttpHeaderNames.ACCEPT, encoding.responseType);
        }
        if (contentType != null) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        final var response = dispatch(request);
        assertErrorResponse(response, encoding, ErrorTag.DATA_MISSING, UNMAPPED_REQUEST_ERROR);
    }

    private static Stream<Arguments> unmappedRequest() {
        return Stream.of(
            // no processor matching api resource
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, "/", null),
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, BASE_PATH, null),
            Arguments.of(DEFAULT_ENCODING, HttpMethod.GET, BASE_PATH + "/test", null),
            // valid URI, unsupported request content-type (1 per case where it's checked)
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DATA_PATH, TEXT_PLAIN),
            Arguments.of(TestEncoding.XML, HttpMethod.PUT, DATA_PATH, TEXT_PLAIN),
            Arguments.of(TestEncoding.XML, HttpMethod.PATCH, DATA_PATH, TEXT_PLAIN),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, OPERATIONS_WITH_ID_PATH, TEXT_PLAIN),
            // empty apiPath when required
            Arguments.of(TestEncoding.JSON, HttpMethod.DELETE, DATA_PATH, null),
            Arguments.of(TestEncoding.JSON, HttpMethod.POST, OPERATIONS_PATH, APPLICATION_JSON),
            // valid URI, unsupported HTTP method (1 per URI used)
            Arguments.of(TestEncoding.XML, HttpMethod.PUT, OPERATIONS_PATH, null),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + YANG_LIBRARY_VERSION, null),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, BASE_PATH + MODULES, null),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DISCOVERY_BASE + HOST_META, null),
            Arguments.of(TestEncoding.XML, HttpMethod.POST, DISCOVERY_BASE + HOST_META_JSON, null)
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
