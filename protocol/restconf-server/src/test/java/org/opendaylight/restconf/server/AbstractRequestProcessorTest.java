/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;

@ExtendWith(MockitoExtension.class)
public class AbstractRequestProcessorTest {
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    protected static final String RESTS = "rests";
    protected static final String XML_CONTENT = "xml-content";
    protected static final String JSON_CONTENT = "json-content";

    @Mock
    protected RestconfServer service;
    @Mock
    private PrincipalService principalService;
    @Mock
    private FutureCallback<FullHttpResponse> callback;
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new RestconfRequestDispatcher(service, principalService,
            RESTS, ERROR_TAG_MAPPING, APPLICATION_JSON, PRETTY_PRINT);
    }

    protected FullHttpResponse dispatch(final FullHttpRequest request) {
        dispatcher.dispatch(request, callback);
        verify(callback).onSuccess(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    protected static Stream<Arguments> encodings() {
        return Stream.of(
            Arguments.of(ProcessorTestUtils.TestEncoding.JSON, JSON_CONTENT),
            Arguments.of(ProcessorTestUtils.TestEncoding.XML, XML_CONTENT)
        );
    }
}
