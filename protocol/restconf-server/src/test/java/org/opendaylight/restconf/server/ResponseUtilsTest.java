/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_DATA_JSON;
import static org.opendaylight.restconf.server.PathParameters.DATA;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.spi.ErrorTagMapping.ERRATA_5565;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;

class ResponseUtilsTest {
    private static final String RESTS = "rests";
    private static final String DATA_PATH = "/" + RESTS + DATA + "/";
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    @Mock
    RestconfServer service;
    @Mock
    PrincipalService principalService;
    @Mock
    FutureCallback<FullHttpResponse> callback;

    @Captor
    ArgumentCaptor<FullHttpResponse> responseCaptor;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new RestconfRequestDispatcher(service, principalService,
            RESTS, ERRATA_5565, APPLICATION_JSON, PRETTY_PRINT);
    }

    @Test
    void testHandleException() {
        final var request = buildRequest(HttpMethod.GET, DATA_PATH, ProcessorTestUtils.TestEncoding.JSON, null);
        try (var mockedDataRequestProcessor = mockStatic(DataRequestProcessor.class)) {
            mockedDataRequestProcessor.when(() -> DataRequestProcessor.processDataRequest(any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));
            dispatcher.dispatch(request, callback);

            verify(callback).onSuccess(responseCaptor.capture());
            FullHttpResponse response = responseCaptor.getValue();
            assertEquals(500, response.status().code());
            assertEquals(APPLICATION_YANG_DATA_JSON.toString(), response.headers().get("Content-Type"));
        }
    }
}
