/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.server.PrincipalService;

@ExtendWith(MockitoExtension.class)
class OpenApiRequestDispatcherTest {
    private static final String BASE_PATH = "/openapi";
    private static final String API_V3_PATH = BASE_PATH + "/api/v3";
    private static final String BASE_URL = "http://127.0.0.1:8184" + BASE_PATH;
    private static final String HOME_PAGE = BASE_URL + "/explorer/index.html";
    private static final URI OPENAPI_BASE_URI = URI.create(BASE_URL);
    private static final URI RESTCONF_SERVER_URI = URI.create("http://127.0.0.1:8182");

    @Mock
    private PrincipalService principalService;
    @Mock
    private OpenApiService service;
    @Mock
    private FutureCallback<FullHttpResponse> callback;
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

    private OpenApiRequestDispatcher dispatcher;

    @BeforeEach
    void beforeEach() {
        doReturn(null).when(principalService).acquirePrincipal(any());
        dispatcher = new OpenApiRequestDispatcher(principalService, service, OPENAPI_BASE_URI, RESTCONF_SERVER_URI);
    }

    @ParameterizedTest
    @MethodSource
    void staticContent(final String resource, final String mediaType) throws Exception {
        final byte[] expected;
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in);
            expected = in.readAllBytes();
        }
        final var response = dispatch(BASE_PATH + resource);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(mediaType, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(expected.length, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        final var actual = ByteBufUtil.getBytes(response.content());
        assertArrayEquals(expected, actual);
    }

    private static Stream<Arguments> staticContent() {
        return Stream.of(
            Arguments.of("/explorer/index.html", "text/html"),
            Arguments.of("/explorer/logo_small.png", "image/png"),
            Arguments.of("/explorer/swagger-ui/swagger-ui.css", "text/css"),
            Arguments.of("/explorer/swagger-ui/swagger-ui.js", "text/javascript"),
            Arguments.of("/explorer/swagger-ui/swagger-ui.js.map", "application/octet-stream")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {BASE_PATH, API_V3_PATH})
    void homePageRedirect(final String base) {
        final var response = dispatch(base + "/ui");
        assertEquals(HttpResponseStatus.SEE_OTHER, response.status());
        assertEquals(HOME_PAGE, response.headers().get(HttpHeaderNames.LOCATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unknown", BASE_PATH + "/explorer/unknown-resource.png"})
    void notFound(final String uri) {
        final var response = dispatch(BASE_PATH + uri);
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
    }



    private FullHttpResponse dispatch(final String uri) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        dispatcher.dispatch(request, callback);
        verify(callback, timeout(1000)).onSuccess(responseCaptor.capture());
        final var response = responseCaptor.getValue();
        assertNotNull(response);
        return response;
    }
}
