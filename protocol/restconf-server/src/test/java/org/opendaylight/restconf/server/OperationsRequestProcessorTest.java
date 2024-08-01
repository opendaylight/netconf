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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.PathParameters.OPERATIONS;
import static org.opendaylight.restconf.server.ProcessorTestUtils.API_PATH;
import static org.opendaylight.restconf.server.ProcessorTestUtils.ID_PATH;
import static org.opendaylight.restconf.server.ProcessorTestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertInputContent;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.ProcessorTestUtils.formattableBody;
import static org.opendaylight.restconf.server.spi.ErrorTagMapping.ERRATA_5565;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.ProcessorTestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

@ExtendWith(MockitoExtension.class)
public class OperationsRequestProcessorTest {
    private static final String RESTS = "rests";
    private static final String OPERATIONS_PATH = "/" + RESTS + OPERATIONS;
    private static final String OPERATIONS_PATH_WITH_ID = OPERATIONS_PATH + "/" + ID_PATH;
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;
    private static final String XML_CONTENT = "xml-content";
    private static final String JSON_CONTENT = "json-content";

    @Mock
    RestconfServer service;
    @Mock
    PrincipalService principalService;
    @Mock
    FutureCallback<FullHttpResponse> callback;

    @Captor
    ArgumentCaptor<FullHttpResponse> responseCaptor;
    @Captor
    ArgumentCaptor<ApiPath> apiPathCaptor;
    @Captor
    ArgumentCaptor<OperationInputBody> operationsPostInputCaptor;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new RestconfRequestDispatcher(service, principalService,
            RESTS, ERRATA_5565, APPLICATION_JSON, PRETTY_PRINT);
    }

    private static Stream<Arguments> encodings() {
        return Stream.of(
            Arguments.of(TestEncoding.JSON, JSON_CONTENT),
            Arguments.of(TestEncoding.XML, XML_CONTENT)
        );
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
    @MethodSource("encodings")
    void postOperationsRoot(final TestEncoding encoding, final String content) {
        final var request = buildRequest(HttpMethod.POST, OPERATIONS_PATH, encoding, content);
        final var response = dispatch(request);

        assertEquals(HttpResponseStatus.NOT_IMPLEMENTED, response.status());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
    }

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

        assertResponse(response, HttpResponseStatus.NO_CONTENT, encoding.responseType, content);
    }

    private FullHttpResponse dispatch(final FullHttpRequest request) {
        dispatcher.dispatch(request, callback);
        verify(callback).onSuccess(responseCaptor.capture());
        return responseCaptor.getValue();
    }
}
