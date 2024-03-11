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
import static org.opendaylight.restconf.server.ProcessorTestUtils.assertResponse;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HostMetaRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String HOST_META_URI = PathParameters.DISCOVERY_BASE + PathParameters.HOST_META;
    private static final String HOST_META_JSON_URI  = PathParameters.DISCOVERY_BASE + PathParameters.HOST_META_JSON;

    @ParameterizedTest
    @MethodSource
    void getHostMeta(final String uri, final String contentTemplate, final AsciiString contentType) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK, contentType, contentTemplate.formatted("/" + RESTS));
    }

    private static Stream<Arguments> getHostMeta() {
        return Stream.of(
            Arguments.of(HOST_META_URI, XRD_TEMPLATE, NettyMediaTypes.APPLICATION_XRD_XML),
            Arguments.of(HOST_META_JSON_URI, JRD_TEMPLATE, NettyMediaTypes.APPLICATION_JSON)
        );
    }

    // fixme method mismatch case
}
