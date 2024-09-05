/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.openapi.netty.OpenApiRequestParameters.RequestType;

class OpenApiRequestParametersTest {
    private static final String BASE_PATH = "/openapi";
    private static final String API_V3 = BASE_PATH + "/api/v3";
    private static final String RESOURCE = "resource";
    private static final String RESOURCE_PATH = "/explorer/swagger-ui/favicon-32x32.png";
    private static final String INSTANCE = "instance";
    private static final String MODULE = "module";
    private static final String REVISION = "revision";
    private static final String WIDTH = "width";
    private static final String DEPTH = "depth";
    private static final String LIMIT = "limit";
    private static final String OFFSET = "offset";
    private static final String[] ALL_PARAMS_NAMES =
        {RESOURCE, INSTANCE, MODULE, REVISION, WIDTH, DEPTH, LIMIT, OFFSET};

    @ParameterizedTest
    @MethodSource
    void uriToParams(final String uri, final RequestType requestType, final Map<String, Object> expectedParams) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        final var params = new OpenApiRequestParameters(BASE_PATH, request);
        assertEquals(requestType, params.requestType());
        for (var paramName : ALL_PARAMS_NAMES) {
            final var expectedValue = expectedParams.get(paramName);
            final var actualValue = switch (paramName) {
                case RESOURCE -> params.resource();
                case INSTANCE -> params.instance();
                case MODULE -> params.module();
                case REVISION -> params.revision();
                case WIDTH -> params.width();
                case DEPTH -> params.depth();
                case LIMIT -> params.limit();
                case OFFSET -> params.offset();
                default -> null;
            };
            assertEquals(expectedValue, actualValue);
        }
    }

    private static Stream<Arguments> uriToParams() {
        return Stream.of(
            Arguments.of(BASE_PATH + RESOURCE_PATH, RequestType.STATIC_CONTENT, Map.of(RESOURCE, RESOURCE_PATH)),
            Arguments.of(BASE_PATH + "/ui", RequestType.UI, Map.of()),
            Arguments.of(API_V3 + "/ui", RequestType.ALT_UI, Map.of()),
            Arguments.of(API_V3 + "/single?width=1&depth=2&limit=3&offset=4",
                RequestType.SINGLE, Map.of(WIDTH, 1, DEPTH, 2, LIMIT, 3, OFFSET, 4)),
            Arguments.of(API_V3 + "/single/meta?limit=5&offset=6",
                RequestType.SINGLE_META, Map.of(LIMIT, 5, OFFSET, 6)),
            Arguments.of(API_V3 + "/module-7?revision=revision-8&width=9&depth=10",
                RequestType.MODULE, Map.of(MODULE, "module-7", REVISION, "revision-8", WIDTH, 9, DEPTH, 10)),
            Arguments.of(API_V3 + "/mounts", RequestType.MOUNTS, Map.of()),
            Arguments.of(API_V3 + "/mounts/instance-11/module-12?revision=revision-13&width=14&depth=15",
                RequestType.MOUNTS_INSTANCE_MODULE, Map.of(INSTANCE, "instance-11", MODULE, "module-12",
                    REVISION, "revision-13", WIDTH, 14, DEPTH, 15)),
            Arguments.of(API_V3 + "/mounts/instance-16?width=17&depth=18&limit=19&offset=20",
                RequestType.MOUNTS_INSTANCE,
                Map.of(INSTANCE, "instance-16", WIDTH, 17, DEPTH, 18, LIMIT, 19, OFFSET, 20)),
            Arguments.of(API_V3 + "/mounts/instance-21/meta?limit=22&offset=23",
                RequestType.MOUNTS_INSTANCE_META, Map.of(INSTANCE, "instance-21", LIMIT, 22, OFFSET, 23)),
            Arguments.of("/unknown-path", RequestType.UNKNOWN, Map.of())
        );
    }
}
