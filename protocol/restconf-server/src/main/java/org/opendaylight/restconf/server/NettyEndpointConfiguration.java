/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Configuration of the Netty RESTCONF server endpoint.
 */
public final class NettyEndpointConfiguration extends EndpointConfiguration {
    // characters not valid in 'pchar' ABNF rule of RFC3986 encoded form
    private static final CharMatcher NOT_PCHAR;

    static {
        //  ALPHA         =  %x41-5A / %x61-7A
        final var alpha = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));
        //  DIGIT         =  %x30-39
        final var digit = CharMatcher.inRange('0', '9');
        //  unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
        final var unreserved = alpha.or(digit).or(CharMatcher.anyOf("-._~"));
        //  sub-delims    = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        final var subDelims = CharMatcher.anyOf("!$&'()*+,;=");
        //  pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
        NOT_PCHAR = unreserved.or(subDelims).or(CharMatcher.is('%')).or(CharMatcher.anyOf(":@")).negate().precomputed();
    }

    private final @NonNull HttpServerStackGrouping transportConfiguration;
    private final @NonNull List<String> apiRootPath;
    private final @NonNull Encoding defaultEncoding;

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis,
            final List<String> apiRootPath, final Encoding defaultEncoding,
            final HttpServerStackGrouping transportConfiguration) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        this.transportConfiguration = requireNonNull(transportConfiguration);
        this.defaultEncoding = requireNonNull(defaultEncoding);

        if (apiRootPath.isEmpty()) {
            throw new IllegalArgumentException("empty apiRootPath");
        }
        if (apiRootPath.getFirst().isEmpty()) {
            throw new IllegalArgumentException("empty first apiRootPath segment");
        }
        this.apiRootPath = List.copyOf(apiRootPath);
    }

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis, final String apiRootPath,
            final Encoding defaultEncoding, final HttpServerStackGrouping transportConfiguration) {
        this(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis,
            parsePathRootless(apiRootPath), defaultEncoding, transportConfiguration);
    }

    @Beta
    public NettyEndpointConfiguration(final HttpServerStackGrouping transportConfiguration) {
        this(ErrorTagMapping.RFC8040, PrettyPrintParam.TRUE, Uint16.ZERO, Uint32.valueOf(10_000), "restconf",
            Encoding.JSON, transportConfiguration);
    }

    /**
     * Parse a string conforming to RFC3986 'path-rootless' ABNF rule into a series of segments.
     *
     * @param str String to parser
     * @return Parsed segments
     * @throws IllegalArgumentException if the string is not valid
     */
    @VisibleForTesting
    static List<String> parsePathRootless(final String str) {
        final var len = str.length();
        if (len == 0) {
            throw new IllegalArgumentException("Empty path");
        }

        //  path-rootless = segment-nz *( "/" segment )
        //  segment       = *pchar
        //  segment-nz    = 1*pchar
        //  pct-encoded   = "%" HEXDIG HEXDIG
        //  HEXDIG         =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
        final var tmp = new ArrayList<String>();
        var offset = 0;
        while (offset < len) {
            final var begin = offset;
            final var slash = str.indexOf('/', begin);
            final var end = slash != -1 ? slash : len;
            offset = end + 1;

            final var segment = str.substring(begin, end);
            final var bad = NOT_PCHAR.indexIn(segment);
            if (bad != -1) {
                final var idx = begin + bad;
                throw new IllegalArgumentException(
                    "Invalid character '%s' at offset %s".formatted(str.charAt(idx), idx));
            }

            final String decoded;
            try {
                decoded = QueryStringDecoder.decodeComponent(segment);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot decode segment '%s' at offset %s".formatted(segment, begin),
                    e);
            }
            tmp.add(decoded);
        }

        if (tmp.getFirst().isEmpty()) {
            throw new IllegalArgumentException("Empty first segment");
        }
        return List.copyOf(tmp);
    }

    /**
     * Returns the path to RESTCONF root API resource, expressed as a non-empty list of segments in unencoded form.
     * The first segment is guaranteed to be non-empty.
     *
     * @return the path to RESTCONF root API resource
     */
    public @NonNull List<String> apiRootPath() {
        return apiRootPath;
    }

    /**
     * Returns the HTTP endpoint configuration.
     *
     * @return the HTTP endpoint configuration
     */
    public @NonNull HttpServerStackGrouping transportConfiguration() {
        return transportConfiguration;
    }

    @Beta
    public @NonNull AsciiString defaultAcceptType() {
        return defaultEncoding.mediaType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorTagMapping(), prettyPrint(), sseMaximumFragmentLength(), sseHeartbeatIntervalMillis(),
            apiRootPath, transportConfiguration, defaultEncoding);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof NettyEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && apiRootPath.equals(other.apiRootPath) && transportConfiguration.equals(other.transportConfiguration)
            && defaultEncoding.equals(other.defaultEncoding);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", apiRootPath.stream()
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/")))
            .add("defaultEncoding", defaultEncoding)
            .add("transportConfiguration", transportConfiguration);
    }

    @NonNullByDefault
    public enum Encoding {
        XML("xml", NettyMediaTypes.APPLICATION_YANG_DATA_XML),
        JSON("json", NettyMediaTypes.APPLICATION_YANG_DATA_JSON);

        private final String id;
        private final AsciiString mediaType;

        Encoding(final String id, final AsciiString mediaType) {
            this.id = requireNonNull(id);
            this.mediaType = requireNonNull(mediaType);
        }

        public AsciiString mediaType() {
            return mediaType;
        }

        public static Encoding from(final String value) {
            requireNonNull(value);
            for (var encoding : values()) {
                if (encoding.id.equalsIgnoreCase(value)) {
                    return encoding;
                }
            }
            throw new IllegalArgumentException("Unsupported encoding value");
        }
    }
}
