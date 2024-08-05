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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.ProcessorTestUtils.buildRequest;
import static org.opendaylight.restconf.server.spi.ErrorTagMapping.ERRATA_5565;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.ProcessorTestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.RestconfServer;

@ExtendWith(MockitoExtension.class)
public class HostMetaRequestProcessorTest {
    private static final String TEST_BASE_PATH = "base-path";
    private static final String WELL_KNOWN = ".well-known";
    private static final String HOST_META = "/host-meta";
    private static final String HOST_META_PATH = "/" + WELL_KNOWN + HOST_META;
    private static final String HOST_META_JSON = "/host-meta.json";
    private static final String HOST_META_JSON_PATH = "/" + WELL_KNOWN + HOST_META_JSON;
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    @Mock
    PrincipalService principalService;
    @Mock
    RestconfServer service;
    @Mock
    FutureCallback<FullHttpResponse> callback;

    @Captor
    ArgumentCaptor<FullHttpResponse> responseCaptor;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new RestconfRequestDispatcher(service, principalService,
            TEST_BASE_PATH, ERRATA_5565, APPLICATION_JSON, PRETTY_PRINT);
    }

    @Test
    void getHostMeta() {
        final var encoding = TestEncoding.XRD;
        final var request = buildRequest(HttpMethod.GET, HOST_META_PATH, encoding, null);
        final var response = dispatch(request);

        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
        assertEquals("""
            <?xml version='1.0' encoding='UTF-8'?>
            <XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>
              <Link rel='restconf' href='/base-path'/>
            </XRD>""", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void getHostMetaJson() {
        final var encoding = TestEncoding.JSON;
        final var request = buildRequest(HttpMethod.GET, HOST_META_JSON_PATH, encoding, null);
        final var response = dispatch(request);

        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
        assertEquals("""
            {
              "links" : {
                "rel" : "restconf",
                "href" : "/base-path"
              }
            }""", response.content().toString(StandardCharsets.UTF_8));
    }

    private FullHttpResponse dispatch(final FullHttpRequest request) {
        dispatcher.dispatch(request, callback);
        verify(callback).onSuccess(responseCaptor.capture());
        return responseCaptor.getValue();
    }
}
