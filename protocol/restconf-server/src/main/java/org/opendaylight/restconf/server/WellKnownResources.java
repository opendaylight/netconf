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
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Well-known resources supported by a particular host.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8615">RFC 8615</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#section-3">RFC 6415, section 3</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6415#appendix-A">RFC 6415, appendix A</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.1">RFC 8040, section 3.1</a>
 */
final class WellKnownResources {
    private static final Logger LOG = LoggerFactory.getLogger(WellKnownResources.class);
    private final ByteBuf jrd;
    private final ByteBuf xrd;

    WellKnownResources(final String restconf) {
        requireNonNull(restconf);
        jrd = newDescriptor("""
            {
                "links" : {
                    "rel" : "restconf",
                    "href" : "%s"
                }
            }""", restconf);
        xrd = newDescriptor("""
            <?xml version='1.0' encoding='UTF-8'?>
            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                <Link rel="restconf" href="%s"/>
            </XRD>""", restconf);
    }

    // The format/args combination here is overly flexible, but it has a secondary use: it defeats SpotBugs analysis.
    // Since 'format' is an argument instead of a literal, simple bytecode analysis cannot point out
    // VA_FORMAT_STRING_USES_NEWLINE.
    private static ByteBuf newDescriptor(final String format, final Object... args) {
        return Unpooled.unreleasableBuffer(
            ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, format.formatted(args)).asReadOnly());
    }

    FullHttpResponse request(final HttpVersion version, final ImplementedMethod method, final SegmentPeeler peeler) {
        final var suffix = QueryStringDecoder.decodeComponent(peeler.remaining());
        return switch (suffix) {
            case "/host-meta" -> requestXRD(version, method);
            case "/host-meta.json" -> requestJRD(version, method);
            default -> {
                LOG.debug("Suffix '{}' not recognized", suffix);
                yield new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND);
            }
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.1
    private FullHttpResponse requestXRD(final HttpVersion version, final ImplementedMethod method) {
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
            default -> methodNotAllowed(version);
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.2
    private FullHttpResponse requestJRD(final HttpVersion version, final ImplementedMethod method) {
        return switch (method) {
            case GET -> getResponse(version, HttpHeaderValues.APPLICATION_JSON, jrd);
            case HEAD -> headResponse(version, HttpHeaderValues.APPLICATION_JSON, jrd);
            case OPTIONS -> optionsResponse(version);
            default -> methodNotAllowed(version);
        };
    }

    private static FullHttpResponse getResponse(final HttpVersion version, final AsciiString contentType,
            final ByteBuf content) {
        return setContentHeaders(new DefaultFullHttpResponse(version, HttpResponseStatus.OK, content.slice()),
            contentType, content);
    }

    private static FullHttpResponse headResponse(final HttpVersion version, final AsciiString contentType,
            final ByteBuf content) {
        return setContentHeaders(new DefaultFullHttpResponse(version, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER),
            contentType, content);
    }

    private static FullHttpResponse optionsResponse(final HttpVersion version) {
        return setAllowHeader(new DefaultFullHttpResponse(version, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER));
    }

    private static FullHttpResponse methodNotAllowed(final HttpVersion version) {
        return setAllowHeader(new DefaultFullHttpResponse(version, HttpResponseStatus.METHOD_NOT_ALLOWED));
    }

    private static FullHttpResponse setAllowHeader(final FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS");
        return response;
    }

    private static FullHttpResponse setContentHeaders(final FullHttpResponse response, final AsciiString contentType,
            final ByteBuf content) {
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        return response;
    }
}
