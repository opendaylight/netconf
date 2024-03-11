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
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.ProcessorTestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertInputContent;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.ProcessorTestUtils.formattableBody;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.ProcessorTestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

class OperationsRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String OPERATIONS_PATH = "/" + RESTS + OPERATIONS;
    private static final String OPERATIONS_PATH_WITH_ID = OPERATIONS_PATH + "/" + ID_PATH;

    @Captor
    ArgumentCaptor<ApiPath> apiPathCaptor;
    @Captor
    ArgumentCaptor<OperationInputBody> operationsPostInputCaptor;

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
    @MethodSource("encodings")
    void postOperationsRoot(final TestEncoding encoding, final String content) {
        final var request = buildRequest(HttpMethod.POST, OPERATIONS_PATH, encoding, content);
        final var response = dispatch(request);

        assertEquals(HttpResponseStatus.NOT_IMPLEMENTED, response.status());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
    }

    // FIXME response with/without content
    @ParameterizedTest
    @MethodSource("encodings")
    void postOperationsWithId(final TestEncoding encoding, final String content) throws Exception {
        final var result = new InvokeResult(formattableBody(encoding, content));
        doAnswer(answerCompleteWith(result)).when(service)
            .operationsPOST(any(), any(), any(ApiPath.class), any(OperationInputBody.class));

        final var request = buildRequest(HttpMethod.POST, OPERATIONS_PATH_WITH_ID, encoding, content);
        final var response = dispatch(request);
        verify(service).operationsPOST(any(), any(), apiPathCaptor.capture(), operationsPostInputCaptor.capture());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        final var expectedClass = encoding.isJson() ? JsonOperationInputBody.class : XmlOperationInputBody.class;
        assertInputContent(operationsPostInputCaptor.getValue(), expectedClass, content);

        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
    }
}
