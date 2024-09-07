/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;

/**
 * Well-known resources supported by a particular host.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8615">RFC 8615</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-3">RFC 6415, section 3</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#appendix-A">RFC 6415, appendix A</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.1">RFC 8040, section 3.1</a>
 */
final class WellKnownResources {
    private final ByteBuf jrd;
    private final ByteBuf xrd;

    WellKnownResources(final String restconf) {
        requireNonNull(restconf);
        jrd = newBuffer("""
            {
                "links" : {
                    "rel" : "restconf",
                    "href" : "%s"
                }
            }""".formatted(restconf));
        xrd = newBuffer("""
            <?xml version='1.0' encoding='UTF-8'?>
            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                <Link rel="restconf" href="%s"/>
            </XRD>""".formatted(restconf));
    }

    private static ByteBuf newBuffer(final String content) {
        return Unpooled.unreleasableBuffer(ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, content));
    }

    FullHttpResponse request(final HttpVersion version, final Method method, final String suffix) {
        return switch (suffix) {
            case "host-meta" -> requestXRD(version, method);
            case "host-meta.json" -> requestJRD(version, method);
            default -> new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND);
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.1
    private FullHttpResponse requestXRD(final HttpVersion version, final Method method) {
        // FIXME: https://www.rfc-editor.org/rfc/rfc6415#appendix-A paragraph 2 says:
        //
        //           The client MAY request a JRD representation using the HTTP "Accept"
        //           request header field with a value of "application/json"
        //
        //        so we should be checking Accept and redirect to requestJRD()
        return switch (method) {
            case GET -> getResponse(version, NettyMediaTypes.APPLICATION_XRD_XML, xrd);
            case HEAD -> headResponse(version, NettyMediaTypes.APPLICATION_XRD_XML, xrd);
            case OPTIONS -> optionsResponse(version);
            default -> new DefaultFullHttpResponse(version, HttpResponseStatus.METHOD_NOT_ALLOWED);
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.2
    private FullHttpResponse requestJRD(final HttpVersion version, final Method method) {
        return switch (method) {
            case GET -> getResponse(version, HttpHeaderValues.APPLICATION_JSON, jrd);
            case HEAD -> headResponse(version, HttpHeaderValues.APPLICATION_JSON, jrd);
            case OPTIONS -> optionsResponse(version);
            default -> new DefaultFullHttpResponse(version, HttpResponseStatus.METHOD_NOT_ALLOWED);
        };
    }

    private static FullHttpResponse getResponse(final HttpVersion version, final AsciiString contentType,
            final ByteBuf content) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK, content);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        return response;
    }

    private static FullHttpResponse headResponse(final HttpVersion version, final AsciiString contentType,
            final ByteBuf content) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        return response;
    }

    private static FullHttpResponse optionsResponse(final HttpVersion version) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS");
        return response;
    }
}
