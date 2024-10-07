/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.AsciiString;
import java.net.URI;
import java.security.Principal;
import java.text.ParseException;
import java.util.List;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.server.api.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for various RESTCONF HTTP resources, either defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3">RFC8040, section 3.3</a>, or those made up by us at some
 * point in the past.
 */
@NonNullByDefault
abstract sealed class AbstractResource permits AbstractLeafResource, APIResource {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractResource.class);

    /**
     * A {@link CompletedRequest} reporting {@code 405 Method Not Allowed} and indicating support only for
     * {@code OPTIONS} method.
     */
    static final CompletedRequest OPTIONS_ONLY_METHOD_NOT_ALLOWED;
    /**
     * A {@link CompletedRequest} reporting {@code 200 OK} and containing only {@code Allow: OPTIONS} header.
     */
    static final CompletedRequest OPTIONS_ONLY_OK;

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newHeaders()
            .set(HttpHeaderNames.ALLOW, "OPTIONS");
        OPTIONS_ONLY_METHOD_NOT_ALLOWED = new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OPTIONS_ONLY_OK = new DefaultCompletedRequest(HttpResponseStatus.OK, headers);
    }

    static final CompletedRequest METHOD_NOT_ALLOWED_READ_ONLY =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_READ_ONLY);
    static final CompletedRequest NOT_FOUND = new DefaultCompletedRequest(HttpResponseStatus.NOT_FOUND);

    static final CompletedRequest NOT_ACCEPTABLE_DATA;
    static final CompletedRequest UNSUPPORTED_MEDIA_TYPE_DATA;
    static final CompletedRequest UNSUPPORTED_MEDIA_TYPE_PATCH;

    static {
        final var factory = DefaultHttpHeadersFactory.headersFactory();

        final var headers = factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, String.join(", ", List.of(
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_XML,
            // FIXME: do not advertize these types
            HttpHeaderValues.APPLICATION_JSON.toString(),
            HttpHeaderValues.APPLICATION_XML.toString(),
            NettyMediaTypes.TEXT_XML.toString())));

        NOT_ACCEPTABLE_DATA = new DefaultCompletedRequest(HttpResponseStatus.NOT_ACCEPTABLE, headers);
        UNSUPPORTED_MEDIA_TYPE_DATA = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, headers);
        UNSUPPORTED_MEDIA_TYPE_PATCH = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE,
            factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, AbstractPendingOptions.ACCEPTED_PATCH_MEDIA_TYPES));
    }

    final EndpointInvariants invariants;

    AbstractResource(final EndpointInvariants invariants) {
        this.invariants = requireNonNull(invariants);
    }

    /**
     * Prepare to service a request, by binding the request HTTP method and the request path to a resource and
     * validating request headers in that context. This method is required to not block.
     *
     * @param peeler the {@link SegmentPeeler} holding the unprocessed part of the request path
     * @param session the {@link TransportSession} on which this request is being invoked
     * @param method the method being invoked
     * @param targetUri the URI of the target resource
     * @param headers request headers
     * @param principal the {@link Principal} making this request, {@code null} if not known
     * @return A {@link PreparedRequest}
     */
    abstract PreparedRequest prepare(SegmentPeeler peeler, TransportSession session, ImplementedMethod method,
        URI targetUri, HttpHeaders headers, @Nullable Principal principal);

    static final PreparedRequest optionalApiPath(final String path, final Function<ApiPath, PreparedRequest> func) {
        return path.isEmpty() ? func.apply(ApiPath.empty()) : requiredApiPath(path, func);
    }

    static final PreparedRequest requiredApiPath(final String path, final Function<ApiPath, PreparedRequest> func) {
        final ApiPath apiPath;
        final var str = path.substring(1);
        try {
            apiPath = ApiPath.parse(str);
        } catch (ParseException e) {
            return badApiPath(str, e);
        }
        return func.apply(apiPath);
    }

    static final CompletedRequest badApiPath(final String path, final ParseException cause) {
        LOG.debug("Failed to parse API path", cause);
        return new DefaultCompletedRequest(HttpResponseStatus.BAD_REQUEST, null,
            ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT,
                "Bad request path '%s': '%s'".formatted(path, cause.getMessage())));
    }

    static final RequestBodyHandling chooseInputEncoding(final HttpHeaders headers) {
        if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            return RequestBodyHandling.NOT_PRESENT;
        }
        final var contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            // No Content-Type
            return RequestBodyHandling.UNSPECIFIED;
        }
        final var mimeType = HttpUtil.getMimeType(contentType);
        if (mimeType == null) {
            // Content-Type without a proper media type
            return RequestBodyHandling.UNSPECIFIED;
        }
        final var mediaType = AsciiString.of(mimeType);
        if (MessageEncoding.JSON.producesDataCompatibleWith(mediaType)) {
            return RequestBodyHandling.JSON;
        }
        if (MessageEncoding.XML.producesDataCompatibleWith(mediaType)) {
            return RequestBodyHandling.XML;
        }
        return RequestBodyHandling.UNRECOGNIZED;
    }

    final @Nullable MessageEncoding chooseOutputEncoding(final HttpHeaders headers) {
        final var acceptValues = headers.getAll(HttpHeaderNames.ACCEPT);
        if (acceptValues.isEmpty()) {
            return invariants.defaultEncoding();
        }

        for (var acceptValue : acceptValues) {
            final var encoding = matchEncoding(acceptValue);
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

    // FIXME: this algorithm is quite naive and ignores https://www.rfc-editor.org/rfc/rfc9110#name-accept, i.e.
    //        it does not handle wildcards at all.
    //        furthermore it completely ignores https://www.rfc-editor.org/rfc/rfc9110#name-quality-values, i.e.
    //        it does not consider client-supplied weights during media type selection AND it treats q=0 as an
    //        inclusion of a media type rather than its exclusion
    private static @Nullable MessageEncoding matchEncoding(final String acceptValue) {
        final var mimeType = HttpUtil.getMimeType(acceptValue);
        if (mimeType != null) {
            final var mediaType = AsciiString.of(mimeType);
            for (var encoding : MessageEncoding.values()) {
                if (encoding.producesDataCompatibleWith(mediaType)) {
                    return encoding;
                }
            }
        }
        return null;
    }
}
