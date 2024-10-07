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
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.BytebufRequestResponse;
import org.opendaylight.netconf.transport.http.CompletedRequest;
import org.opendaylight.netconf.transport.http.EmptyRequestResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
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
@NonNullByDefault
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

    // Well-known resources are immediately available
    CompletedRequest request(final SegmentPeeler peeler, final ImplementedMethod method) {
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? CompletedRequests.OK_OPTIONS
                : CompletedRequests.METHOD_NOT_ALLOWED_OPTIONS;
        }

        final var suffix = QueryStringDecoder.decodeComponent(peeler.remaining());
        return switch (suffix) {
            case "/host-meta" -> requestXRD(method);
            case "/host-meta.json" -> requestJRD(method);
            default -> {
                LOG.debug("Suffix '{}' not recognized", suffix);
                yield CompletedRequests.NOT_FOUND;
            }
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.1
    private CompletedRequest requestXRD(final ImplementedMethod method) {
        // FIXME: https://www.rfc-editor.org/rfc/rfc6415#appendix-A paragraph 2 says:
        //
        //           The client MAY request a JRD representation using the HTTP "Accept"
        //           request header field with a value of "application/json"
        //
        //        so we should be checking Accept and redirect to requestJRD()
        return switch (method) {
            case GET -> getResponse(NettyMediaTypes.APPLICATION_XRD_XML, xrd);
            case HEAD -> headResponse(NettyMediaTypes.APPLICATION_XRD_XML, xrd);
            case OPTIONS -> CompletedRequests.OK_GET;
            default -> CompletedRequests.METHOD_NOT_ALLOWED_GET;
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.2
    private CompletedRequest requestJRD(final ImplementedMethod method) {
        return switch (method) {
            case GET -> getResponse(HttpHeaderValues.APPLICATION_JSON, jrd);
            case HEAD -> headResponse(HttpHeaderValues.APPLICATION_JSON, jrd);
            case OPTIONS -> CompletedRequests.OK_GET;
            default -> CompletedRequests.METHOD_NOT_ALLOWED_GET;
        };
    }

    private static BytebufRequestResponse getResponse(final AsciiString contentType, final ByteBuf content) {
        return new BytebufRequestResponse(HttpResponseStatus.OK, content, contentHeaders(contentType, content));
    }

    private static EmptyRequestResponse headResponse(final AsciiString contentType, final ByteBuf content) {
        return new EmptyRequestResponse(HttpResponseStatus.OK, contentHeaders(contentType, content));
    }

    private static HttpHeaders contentHeaders(final AsciiString contentType, final ByteBuf content) {
        return DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
    }
}
