/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.CompletedRequest;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.LinkRelation;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.HostMeta;
import org.opendaylight.netconf.transport.http.rfc6415.HostMetaJson;
import org.opendaylight.netconf.transport.http.rfc6415.Link;
import org.opendaylight.netconf.transport.http.rfc6415.TargetUri;
import org.opendaylight.netconf.transport.http.rfc6415.XRD;
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
final class WellKnownResources implements XRD {
    private static final Logger LOG = LoggerFactory.getLogger(WellKnownResources.class);
    private static final HeadersResponse XRD_HEAD = HeadersResponse.ofTrusted(HttpResponseStatus.OK,
        HttpHeaderNames.CONTENT_TYPE, HostMeta.MEDIA_TYPE);
    private static final HeadersResponse JRD_HEAD = HeadersResponse.ofTrusted(HttpResponseStatus.OK,
        HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

    private final Map<URI, TargetUri> links;

    WellKnownResources(final String restconf) {
        links = Map.of(LinkRelation.RESTCONF, new TargetUri(LinkRelation.RESTCONF, URI.create(restconf)));
    }

    @Override
    public Stream<? extends Link> links() {
        return links.values().stream().sorted(Comparator.comparing(Link::rel));
    }

    @Override
    public @Nullable Link lookupLink(final URI rel) {
        return links.get(requireNonNull(rel));
    }

    // Well-known resources are immediately available
    CompletedRequest request(final SegmentPeeler peeler, final ImplementedMethod method, final HttpHeaders headers) {
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? AbstractResource.OPTIONS_ONLY_OK
                : AbstractResource.OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        }

        final var suffix = QueryStringDecoder.decodeComponent(peeler.remaining());
        return switch (suffix) {
            case "/host-meta" -> requestXRD(method, headers);
            case "/host-meta.json" -> requestJRD(method);
            default -> {
                LOG.debug("Suffix '{}' not recognized", suffix);
                yield EmptyResponse.NOT_FOUND;
            }
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.1
    private CompletedRequest requestXRD(final ImplementedMethod method, final HttpHeaders headers) {
        // FIXME: https://www.rfc-editor.org/rfc/rfc6415#appendix-A paragraph 2 says:
        //
        //           The client MAY request a JRD representation using the HTTP "Accept"
        //           request header field with a value of "application/json"
        //
        //        so we should be checking Accept and redirect to requestJRD()
        return switch (method) {
            case GET -> new HostMeta(this);
            case HEAD -> XRD_HEAD;
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> AbstractResource.METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    // https://www.rfc-editor.org/rfc/rfc6415#section-6.2
    private CompletedRequest requestJRD(final ImplementedMethod method) {
        return switch (method) {
            case GET -> new HostMetaJson(this);
            case HEAD -> JRD_HEAD;
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> AbstractResource.METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }
}
