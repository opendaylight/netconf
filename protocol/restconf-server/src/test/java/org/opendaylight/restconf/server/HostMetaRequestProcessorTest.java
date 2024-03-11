/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.HostMetaRequestProcessor.JRD_TEMPLATE;
import static org.opendaylight.restconf.server.HostMetaRequestProcessor.XRD_TEMPLATE;
import static org.opendaylight.restconf.server.PathParameters.DISCOVERY_BASE;
import static org.opendaylight.restconf.server.PathParameters.HOST_META;
import static org.opendaylight.restconf.server.PathParameters.HOST_META_JSON;
import static org.opendaylight.restconf.server.TestUtils.assertOptionsResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HostMetaRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String XRD_URI = DISCOVERY_BASE + HOST_META;
    private static final String JRD_URI = DISCOVERY_BASE + HOST_META_JSON;

    @ParameterizedTest
    @ValueSource(strings = {XRD_URI, JRD_URI})
    void options(final String uri) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, uri);
        assertOptionsResponse(dispatch(request), HostMetaRequestProcessor.ALLOW_METHODS);
    }

    @ParameterizedTest
    @MethodSource
    void getHostMeta(final String uri, final String contentTemplate, final AsciiString contentType) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        assertResponse(dispatch(request), HttpResponseStatus.OK, contentType, contentTemplate.formatted(BASE_PATH));
    }

    private static Stream<Arguments> getHostMeta() {
        return Stream.of(
            Arguments.of(XRD_URI, XRD_TEMPLATE, NettyMediaTypes.APPLICATION_XRD_XML),
            Arguments.of(JRD_URI, JRD_TEMPLATE, NettyMediaTypes.APPLICATION_JSON)
        );
    }
}
