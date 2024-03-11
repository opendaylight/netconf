/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_DATA_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_DATA_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_PATCH_JSON;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG_PATCH_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YIN_XML;
import static org.opendaylight.restconf.server.NettyMediaTypes.JSON_TYPES;

import com.google.common.base.MoreObjects;
import com.google.common.io.CharSource;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.mockito.stubbing.Answer;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

final class TestUtils {
    private static final byte[] INVALID_CONTENT = "invalid-content".getBytes(StandardCharsets.UTF_8);
    private static final String ERROR_TYPE_PROTOCOL = ErrorType.PROTOCOL.elementBody();
    private static final Method INPUT_STREAM_METHOD;

    static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;

    static {
        try {
            INPUT_STREAM_METHOD = ConsumableBody.class.getDeclaredMethod("consume");
            INPUT_STREAM_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestUtils() {
        // hidden on purpose
    }

    @SuppressWarnings("unchecked")
    static <T> Answer<Void> answerCompleteWith(T result) {
        return invocation -> {
            // server request is always first arg in RestconfServer
            final var serverRequest =  (ServerRequest<T>) invocation.getArgument(0);
            serverRequest.completeWith(result);
            return null;
        };
    }

    static FullHttpRequest buildRequest(final HttpMethod method, final String uri, final TestEncoding encoding,
            final String content) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        if (method != HttpMethod.GET) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, encoding.requestType);
        }
        request.headers().set(HttpHeaderNames.ACCEPT, encoding.responseType);
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
            protected MoreObjects.ToStringHelper addToStringAttributes(MoreObjects.ToStringHelper helper) {
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

    static <T extends ConsumableBody, S extends T> void assertInputContent(final T inputObj,
            final Class<S> expectedClass, final String expectedContent) throws Exception {
        final var consumable = assertInstanceOf(expectedClass, inputObj);
        try (var input = (InputStream) INPUT_STREAM_METHOD.invoke(consumable)) {
            final var content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expectedContent, content);
        }
    }

    static void assertResponse(final FullHttpResponse response, final HttpResponseStatus expectedStatus) {
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

    static void assertResponseHeaders(final FullHttpResponse response, final Map<CharSequence, Object> headers) {
        for (var entry : headers.entrySet()) {
            assertEquals(String.valueOf(entry.getValue()), String.valueOf(response.headers().get(entry.getKey())));
        }
    }

    static void assertErrorResponse(final FullHttpResponse response, TestEncoding encoding,
            final ErrorTag expectedErrorTag, final String expectedMessage) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CONTENT_TYPE, encoding.responseType));
        assertErrorContent(response.content().toString(StandardCharsets.UTF_8), encoding,
            expectedErrorTag, expectedMessage);
    }

    static void assertErrorContent(final String content, final TestEncoding encoding, final ErrorTag expectedErrorTag,
            final String expectedMessage) {
        // simplified encoding validation
        if (encoding.isJson()) {
            assertTrue(content.startsWith("{"));
        } else {
            assertTrue(content.startsWith("<"));
        }
        // simplified message content validation
        final var tagString = expectedErrorTag.elementBody();
        assertTrue(content.indexOf(ERROR_TYPE_PROTOCOL) > 0, "Should contain `error-type` " + ERROR_TYPE_PROTOCOL);
        assertTrue(content.indexOf(tagString) > 0, "Should contain `error-tag` " + tagString);
        assertTrue(content.indexOf(expectedMessage) > 0, "should contain message " + expectedMessage);
    }

    enum TestEncoding {
        XML(APPLICATION_XML, APPLICATION_YANG_DATA_XML),
        JSON(APPLICATION_JSON, APPLICATION_YANG_DATA_JSON),
        YANG_PATCH_XML(APPLICATION_YANG_PATCH_XML, APPLICATION_YANG_DATA_XML),
        YANG_PATCH_JSON(APPLICATION_YANG_PATCH_JSON, APPLICATION_YANG_DATA_JSON),
        YANG(null, APPLICATION_YANG),
        YIN(null, APPLICATION_YIN_XML);

        AsciiString requestType;
        AsciiString responseType;

        TestEncoding(AsciiString requestType, AsciiString responseType) {
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
