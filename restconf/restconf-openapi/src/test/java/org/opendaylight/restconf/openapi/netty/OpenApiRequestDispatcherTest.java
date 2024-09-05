/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

//import static org.junit.jupiter.api.Assertions.assertArrayEquals;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doReturn;
//import static org.mockito.Mockito.reset;
//import static org.mockito.Mockito.timeout;
//import static org.mockito.Mockito.verify;
//
//import com.google.common.util.concurrent.FutureCallback;
//import io.netty.buffer.ByteBufUtil;
//import io.netty.handler.codec.http.DefaultFullHttpRequest;
//import io.netty.handler.codec.http.FullHttpRequest;
//import io.netty.handler.codec.http.FullHttpResponse;
//import io.netty.handler.codec.http.HttpHeaderNames;
//import io.netty.handler.codec.http.HttpHeaderValues;
//import io.netty.handler.codec.http.HttpMethod;
//import io.netty.handler.codec.http.HttpResponseStatus;
//import io.netty.handler.codec.http.HttpVersion;
//import java.io.InputStream;
//import java.net.URI;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Stream;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.MethodSource;
//import org.junit.jupiter.params.provider.ValueSource;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.opendaylight.restconf.openapi.api.OpenApiService;
//import org.opendaylight.restconf.openapi.model.MountPointInstance;
//import org.opendaylight.restconf.server.PrincipalService;
//import org.skyscreamer.jsonassert.JSONAssert;
//import org.skyscreamer.jsonassert.JSONCompareMode;

//@ExtendWith(MockitoExtension.class)
class OpenApiRequestDispatcherTest {
//    private static final String BASE_PATH = "/openapi";
//    private static final String API_V3_PATH = BASE_PATH + "/api/v3";
//    private static final String BASE_URL = "http://127.0.0.1:8184" + BASE_PATH;
//    private static final String HOME_PAGE = BASE_URL + "/explorer/index.html";
//    private static final URI OPENAPI_BASE_URI = URI.create(BASE_URL);
//    private static final URI RESTCONF_SERVER_URI = URI.create("http://127.0.0.1:8182");
//
//    private static final String JSON_CONTENT = "json-content";
//    private static final byte[] JSON_CONTENT_BYTES = JSON_CONTENT.getBytes(StandardCharsets.UTF_8);
//    private static final String APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON.toString();
//    private static final String MODULE = "module-id";
//    private static final String REVISION = "2024-09-24";
//    private static final String INSTANCE = "instance-id";
//    private static final Integer WIDTH = 1101;
//    private static final Integer DEPTH = 2202;
//    private static final Integer OFFSET = 3303;
//    private static final Integer LIMIT = 4404;
//
//    @Mock
//    private PrincipalService principalService;
//    @Mock
//    private OpenApiService openApiService;
//    @Mock
//    private InputStream resultStream;
//    @Mock
//    private FutureCallback<FullHttpResponse> callback;
//    @Captor
//    private ArgumentCaptor<FullHttpResponse> responseCaptor;
//
//    private OpenApiRequestDispatcher dispatcher;
//
//    @BeforeEach
//    void beforeEach() {
//        doReturn(null).when(principalService).acquirePrincipal(any());
//        dispatcher = new OpenApiRequestDispatcher(principalService, openApiService,
//            OPENAPI_BASE_URI, RESTCONF_SERVER_URI);
//    }
//
//    @ParameterizedTest
//    @MethodSource
//    void staticContent(final String resource, final String mediaType) throws Exception {
//        final byte[] expected;
//        try (var in = getClass().getResourceAsStream(resource)) {
//            assertNotNull(in);
//            expected = in.readAllBytes();
//        }
//        final var uri = BASE_PATH + resource;
//        final var response = dispatch(uri);
//        assertEquals(HttpResponseStatus.OK, response.status());
//        assertEquals(mediaType, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//        assertEquals(expected.length, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
//        final var actual = ByteBufUtil.getBytes(response.content());
//        assertArrayEquals(expected, actual);
//        final var etag =  response.headers().get(HttpHeaderNames.ETAG);
//        assertNotNull(etag);
//        // subsequent request with check by etag
//        reset(callback);
//        final var etagCheckRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
//        etagCheckRequest.headers().set(HttpHeaderNames.IF_NONE_MATCH, etag);
//        final var etagCheckResponse = dispatch(etagCheckRequest);
//        assertEquals(HttpResponseStatus.NOT_MODIFIED, etagCheckResponse.status());
//        assertEquals(etag, response.headers().get(HttpHeaderNames.ETAG));
//        assertEquals(0, etagCheckResponse.content().readableBytes());
//    }
//
//    private static Stream<Arguments> staticContent() {
//        return Stream.of(
//            Arguments.of("/explorer/index.html", "text/html"),
//            Arguments.of("/explorer/logo_small.png", "image/png"),
//            Arguments.of("/explorer/swagger-ui/swagger-ui.css", "text/css"),
//            Arguments.of("/explorer/swagger-ui/swagger-ui.js", "text/javascript"),
//            Arguments.of("/explorer/swagger-ui/swagger-ui.js.map", "application/octet-stream")
//        );
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {BASE_PATH, API_V3_PATH})
//    void homePageRedirect(final String base) {
//        final var response = dispatch(base + "/ui");
//        assertEquals(HttpResponseStatus.SEE_OTHER, response.status());
//        assertEquals(HOME_PAGE, response.headers().get(HttpHeaderNames.LOCATION));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {"/unknown", BASE_PATH + "/explorer/unknown-resource.png"})
//    void notFound(final String uri) {
//        final var response = dispatch(BASE_PATH + uri);
//        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
//    }
//
//    @Test
//    void mountsList() throws Exception {
//        doReturn(List.of(
//            new MountPointInstance("instance-1", 101L),
//            new MountPointInstance("instance-2", 102L))
//        ).when(openApiService).getListOfMounts();
//        final var response = dispatch(API_V3_PATH + "/mounts/");
//        assertEquals(HttpResponseStatus.OK, response.status());
//        assertEquals(APPLICATION_JSON, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//        JSONAssert.assertEquals("""
//            [
//                {
//                    "instance": "instance-1",
//                    "id": 101
//                },
//                {
//                    "instance": "instance-2",
//                    "id": 102
//                }
//            ]""", response.content().toString(StandardCharsets.UTF_8), JSONCompareMode.LENIENT);
//    }
//
//    @Test
//    void emptyMountsList() throws Exception {
//        doReturn(List.of()).when(openApiService).getListOfMounts();
//        final var response = dispatch(API_V3_PATH + "/mounts");
//        assertEquals(HttpResponseStatus.OK, response.status());
//        assertEquals(APPLICATION_JSON, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//        JSONAssert.assertEquals("[]", response.content().toString(StandardCharsets.UTF_8), JSONCompareMode.LENIENT);
//    }
//
//    @Test
//    void allModulesDocs() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService).getAllModulesDoc(RESTCONF_SERVER_URI, WIDTH, DEPTH, OFFSET,
//            LIMIT);
//        final var uri = (API_V3_PATH + "/single?width=%d&depth=%d&offset=%d&limit=%d")
//            .formatted(WIDTH, DEPTH, OFFSET, LIMIT);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    @Test
//    void allModulesMeta() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService).getAllModulesMeta(OFFSET, LIMIT);
//        final var uri = (API_V3_PATH + "/single/meta?offset=%d&limit=%d").formatted(OFFSET, LIMIT);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    @Test
//    void moduleDocs() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService).getDocByModule(MODULE, REVISION, RESTCONF_SERVER_URI, WIDTH,
//            DEPTH);
//        final var uri = (API_V3_PATH + "/%s?revision=%s&width=%d&depth=%d").formatted(MODULE, REVISION, WIDTH, DEPTH);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    @Test
//    void mountInstanceDocs() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService)
//            .getMountDoc(INSTANCE, RESTCONF_SERVER_URI, WIDTH, DEPTH, OFFSET, LIMIT);
//        final var uri = (API_V3_PATH + "/mounts/%s?width=%d&depth=%d&offset=%d&limit=%d")
//            .formatted(INSTANCE, WIDTH, DEPTH, OFFSET, LIMIT);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    @Test
//    void mountInstanceMeta() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService).getMountMeta(INSTANCE, OFFSET, LIMIT);
//        final var uri = (API_V3_PATH + "/mounts/%s/meta?offset=%d&limit=%d").formatted(INSTANCE, OFFSET, LIMIT);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    @Test
//    void moduleInstanceModuleDocs() throws Exception {
//        doReturn(JSON_CONTENT_BYTES).when(resultStream).readAllBytes();
//        doReturn(resultStream).when(openApiService)
//            .getMountDocByModule(INSTANCE, MODULE, REVISION, RESTCONF_SERVER_URI, WIDTH, DEPTH);
//        final var uri = (API_V3_PATH + "/mounts/%s/%s?revision=%s&width=%d&depth=%d")
//            .formatted(INSTANCE, MODULE, REVISION, WIDTH, DEPTH);
//        assertJsonResponse(dispatch(uri));
//    }
//
//    private static void assertJsonResponse(final FullHttpResponse response) {
//        assertEquals(HttpResponseStatus.OK, response.status());
//        assertEquals(APPLICATION_JSON, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//        assertEquals(JSON_CONTENT, response.content().toString(StandardCharsets.UTF_8));
//    }
//
//    private FullHttpResponse dispatch(final String uri) {
//        return dispatch(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
//    }
//
//    private FullHttpResponse dispatch(final FullHttpRequest request) {
//        dispatcher.dispatch(request, callback);
//        verify(callback, timeout(1000)).onSuccess(responseCaptor.capture());
//        final var response = responseCaptor.getValue();
//        assertNotNull(response);
//        return response;
//    }
}
