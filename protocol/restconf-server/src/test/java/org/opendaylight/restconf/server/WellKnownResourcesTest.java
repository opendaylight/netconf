/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.opendaylight.restconf.server.TestUtils.assertOptionsResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponseHeaders;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendaylight.netconf.transport.http.FiniteResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.HostMeta;

class WellKnownResourcesTest {
    private static final String XRD_SUFFIX = "/host-meta";
    private static final String JRD_SUFFIX = "/host-meta.json";
    private static final WellKnownResources RESOURCES = new WellKnownResources("testRestconf");
    private static final HttpHeaders EMPTY_HEADERS = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders();

    @ParameterizedTest
    @ValueSource(strings = { XRD_SUFFIX, JRD_SUFFIX })
    void options(final String uri) throws Exception {
        final var response = RESOURCES.request(new SegmentPeeler(uri), ImplementedMethod.OPTIONS, EMPTY_HEADERS)
            .asResponse();

        assertOptionsResponse(assertInstanceOf(HeadersResponse.class, response).toHttpResponse(HttpVersion.HTTP_1_1),
            "GET, HEAD, OPTIONS");
    }

    @ParameterizedTest
    @MethodSource
    void getHostMeta(final String uri, final AsciiString contentType, final String content) throws Exception {
        final var response = RESOURCES.request(new SegmentPeeler(uri), ImplementedMethod.GET, EMPTY_HEADERS)
            .asResponse();
        assertResponse(assertInstanceOf(FiniteResponse.class, response)
            .toReadyResponse(UnpooledByteBufAllocator.DEFAULT).toHttpResponse(HttpVersion.HTTP_1_1),
            HttpResponseStatus.OK, contentType, content);
    }

    private static Stream<Arguments> getHostMeta() {
        return Stream.of(
            Arguments.of(XRD_SUFFIX, HostMeta.MEDIA_TYPE, """
                <?xml version="1.0" ?>
                <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                  <Link rel="restconf" href="testRestconf"/>
                </XRD>"""),
            Arguments.of(JRD_SUFFIX, HttpHeaderValues.APPLICATION_JSON, """
                {
                  "links" : [
                    {
                      "rel" : "restconf",
                      "href" : "testRestconf"
                    }
                  ]
                }""")
        );
    }

    @Test
    void putHostMeta() throws Exception {
        final var response = assertInstanceOf(HeadersResponse.class,
            RESOURCES.request(new SegmentPeeler(JRD_SUFFIX), ImplementedMethod.POST, EMPTY_HEADERS).asResponse())
            .toHttpResponse(HttpVersion.HTTP_1_1);
        assertResponse(response, HttpResponseStatus.METHOD_NOT_ALLOWED);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    }
}
