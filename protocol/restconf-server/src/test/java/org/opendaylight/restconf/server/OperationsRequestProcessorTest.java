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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertInputContent;
import static org.opendaylight.restconf.server.TestUtils.assertOptionsResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.TestUtils.formattableBody;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

class OperationsRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String OPERATIONS_PATH = BASE_PATH + OPERATIONS;
    private static final String OPERATIONS_PATH_WITH_ID = OPERATIONS_PATH + "/" + ID_PATH;
    private static final URI RESTCONF_URI = URI.create(BASE_URI.toString().concat("/"));

    @Captor
    ArgumentCaptor<ApiPath> apiPathCaptor;
    @Captor
    ArgumentCaptor<OperationInputBody> inputCaptor;

    @ParameterizedTest
    @ValueSource(strings = {OPERATIONS_PATH, OPERATIONS_PATH_WITH_ID})
    void options(final String uri) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, uri);
        assertOptionsResponse(dispatch(request), "GET, HEAD, OPTIONS, POST");
    }


    @ParameterizedTest
    @MethodSource("encodings")
    void getOperationsRoot(final TestEncoding encoding, final String content) {
        final var result = formattableBody(encoding, content);
        doAnswer(answerCompleteWith(result)).when(service).operationsGET(any());

        final var request = buildRequest(HttpMethod.GET, OPERATIONS_PATH, encoding, null);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void getOperationsWithId(final TestEncoding encoding, final String content) {
        final var result = formattableBody(encoding, content);
        doAnswer(answerCompleteWith(result)).when(service).operationsGET(any(), any(ApiPath.class));

        final var request = buildRequest(HttpMethod.GET, OPERATIONS_PATH_WITH_ID, encoding, null);
        final var response = dispatch(request);
        verify(service).operationsGET(any(), apiPathCaptor.capture());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }

    @ParameterizedTest
    @MethodSource
    void postOperations(final TestEncoding encoding, final String input, final String output) throws Exception {
        final var result = new InvokeResult(output == null ? null : formattableBody(encoding, output));
        doAnswer(answerCompleteWith(result)).when(service)
            .operationsPOST(any(), any(), any(ApiPath.class), any(OperationInputBody.class));

        final var request = buildRequest(HttpMethod.POST, OPERATIONS_PATH_WITH_ID, encoding, input);
        final var response = dispatch(request);
        verify(service).operationsPOST(any(), eq(RESTCONF_URI), apiPathCaptor.capture(), inputCaptor.capture());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        final var expectedClass = encoding.isJson() ? JsonOperationInputBody.class : XmlOperationInputBody.class;
        assertInputContent(inputCaptor.getValue(), expectedClass, input);

        if (output == null) {
            assertResponse(response, HttpResponseStatus.NO_CONTENT);
        } else {
            assertResponse(response, HttpResponseStatus.OK, encoding.responseType, output);
        }
    }

    private static Stream<Arguments> postOperations() {
        return Stream.of(
            Arguments.of(TestEncoding.XML, XML_CONTENT, null),
            Arguments.of(TestEncoding.XML, XML_CONTENT, "xml-output"),
            Arguments.of(TestEncoding.JSON, JSON_CONTENT, null),
            Arguments.of(TestEncoding.JSON, JSON_CONTENT, "json-output")
        );
    }

    @Test
    void postOperationsNoContent() throws Exception {
        final var result = new InvokeResult(null);
        doAnswer(answerCompleteWith(result)).when(service)
            .operationsPOST(any(), any(), any(ApiPath.class), any(OperationInputBody.class));

        // post request with no content and no content-type header
        // can be used to invoke rpc with no input defined
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, OPERATIONS_PATH_WITH_ID);
        final var response = dispatch(request);
        verify(service).operationsPOST(any(), eq(RESTCONF_URI), apiPathCaptor.capture(), inputCaptor.capture());
        assertEquals(API_PATH, apiPathCaptor.getValue());

        // empty body expected to be passed using a wrapper object of server default encoding
        final var expectedClass =
            DEFAULT_ENCODING.isJson() ? JsonOperationInputBody.class : XmlOperationInputBody.class;
        assertInputContent(inputCaptor.getValue(), expectedClass, "");
        assertResponse(response, HttpResponseStatus.NO_CONTENT);
    }
}
