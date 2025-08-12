/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_DATA_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_DATA_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_PATCH_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_PATCH_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YIN_XML;

import com.google.common.base.MoreObjects;
import com.google.common.io.CharSource;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.stubbing.Answer;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

final class TestUtils {
    private static final byte[] INVALID_CONTENT = "invalid-content".getBytes(StandardCharsets.UTF_8);

    static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;

    private TestUtils() {
        // hidden on purpose
    }

    static <T> Answer<Void> answerCompleteWith(final T result) {
        return invocation -> {
            // server request is always first arg in RestconfServer
            invocation.<ServerRequest<T>>getArgument(0).completeWith(result);
            return null;
        };
    }

    static FullHttpRequest newOptionsRequest(final String uri) {
        return newRequest(HttpMethod.OPTIONS, uri);
    }

    static FullHttpRequest newRequest(final HttpMethod method, final String uri) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        request.headers().set(HttpHeaderNames.HOST, AbstractRequestProcessorTest.HOST);
        return request;
    }

    static FullHttpRequest buildRequest(final HttpMethod method, final String uri, final TestEncoding encoding,
            final String content) {
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        final var headers = request.headers()
            .set(HttpHeaderNames.HOST, AbstractRequestProcessorTest.HOST)
            .set(HttpHeaderNames.ACCEPT, encoding.responseType);
        if (!HttpMethod.GET.equals(method)) {
            final var buf = request.content();
            buf.writeBytes(content.getBytes(StandardCharsets.UTF_8));
            headers
                .set(HttpHeaderNames.CONTENT_TYPE, encoding.requestType)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        }
        return request;
    }

    static FormattableBody formattableBody(final TestEncoding encoding, final String content) {
        final var isJson = encoding.isJson();
        return new FormattableBody() {
            @Override
            public void formatToJSON(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
                out.write(isJson ? content.getBytes(StandardCharsets.UTF_8) : INVALID_CONTENT);
            }

            @Override
            public void formatToXML(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
                out.write(!isJson ? content.getBytes(StandardCharsets.UTF_8) : INVALID_CONTENT);
            }

            @Override
            protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper helper) {
                return helper;
            }
        };
    }

    static CharSource charSource(final String content) {
        return new CharSource() {
            @Override
            public Reader openStream() throws IOException {
                return new StringReader(content);
            }
        };
    }

    static void assertResponse(final HttpResponse response, final HttpResponseStatus expectedStatus) {
        assertNotNull(response);
        assertEquals(expectedStatus, response.status());
    }

    static void assertResponse(final FullHttpResponse response, final HttpResponseStatus expectedStatus,
            final CharSequence expectedContentType, final String expectedContent) {
        assertResponse(response, expectedStatus);
        final var contentBuf = response.content();
        assertTrue(contentBuf.readableBytes() > 0);
        assertEquals(expectedContent, contentBuf.toString(StandardCharsets.UTF_8));
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, expectedContentType));
    }

    static void assertResponseHeaders(final HttpResponse response, final Map<CharSequence, Object> headers) {
        for (var entry : headers.entrySet()) {
            final var headerName = entry.getKey().toString();
            final var headerValue = String.valueOf(entry.getValue());
            assertTrue(response.headers().contains(headerName, headerValue, true),
                "Response should contain header " + headerName + " = " + headerValue);
        }
    }

    static void assertErrorResponse(final FullHttpResponse response, final TestEncoding encoding,
            final ErrorTag expectedErrorTag, final String expectedMessage) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
        assertErrorContent(response.content().toString(StandardCharsets.UTF_8), encoding,
            expectedErrorTag, expectedMessage);
    }

    static void assertErrorContent(final String content, final TestEncoding encoding, final ErrorTag expectedErrorTag,
            final String expectedMessage) {
        assertContentSimplified(content, encoding,
            List.of(ErrorType.PROTOCOL.elementBody(), expectedErrorTag.elementBody(), expectedMessage));
    }

    static void assertContentSimplified(final String content, final TestEncoding encoding,
            final List<String> expectedWithin) {
        // simplified encoding validation
        if (encoding.isJson()) {
            assertTrue(content.startsWith("{"), "Not JSON content");
        } else {
            assertTrue(content.startsWith("<"), "Not XML content");
        }
        // simplified message content validation
        for (var expected : expectedWithin) {
            assertTrue(content.indexOf(expected) > 0, "Content missing expected " + expected);
        }
    }

    static void assertOptionsResponse(final FullHttpResponse response, final String expectedAllowHeader) {
        assertEquals(HttpResponseStatus.OK, response.status());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, expectedAllowHeader));
    }

    enum TestEncoding {
        XML(HttpHeaderValues.APPLICATION_XML, APPLICATION_YANG_DATA_XML),
        JSON(HttpHeaderValues.APPLICATION_JSON, APPLICATION_YANG_DATA_JSON),
        YANG_PATCH_XML(APPLICATION_YANG_PATCH_XML, APPLICATION_YANG_DATA_XML),
        YANG_PATCH_JSON(APPLICATION_YANG_PATCH_JSON, APPLICATION_YANG_DATA_JSON),
        YANG(null, APPLICATION_YANG),
        YIN(null, APPLICATION_YIN_XML);

        private static final Set<AsciiString> JSON_TYPES = Set.of(
            NettyMediaTypes.APPLICATION_YANG_DATA_JSON, NettyMediaTypes.APPLICATION_YANG_PATCH_JSON,
            HttpHeaderValues.APPLICATION_JSON);

        AsciiString requestType;
        AsciiString responseType;

        TestEncoding(final AsciiString requestType, final AsciiString responseType) {
            this.requestType = requestType;
            this.responseType = responseType;
        }

        boolean isJson() {
            return JSON_TYPES.contains(responseType);
        }

        boolean isYin() {
            return APPLICATION_YIN_XML.equals(responseType);
        }
    }
}
