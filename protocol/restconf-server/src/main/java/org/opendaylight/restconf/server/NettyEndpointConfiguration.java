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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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
    private final @NonNull MessageEncoding defaultEncoding;
    private final @NonNull Uint32 chunkSize;
    private final @NonNull Uint32 frameSize;
    private final @Nullable String altSvcHeaderValue;

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis,
            final List<String> apiRootPath, final MessageEncoding defaultEncoding,
            final HttpServerStackGrouping transportConfiguration, final Uint32 chunkSize, final Uint32 frameSize,
            final String altSvcHeaderValue) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        this.transportConfiguration = requireNonNull(transportConfiguration);
        this.defaultEncoding = requireNonNull(defaultEncoding);
        this.altSvcHeaderValue = altSvcHeaderValue;

        if (chunkSize.intValue() < 1) {
            throw new IllegalArgumentException("Chunks have to have at least one byte");
        }
        this.chunkSize = chunkSize;

        if (frameSize.intValue() < 16384 || frameSize.intValue() > 16777215) {
            throw new IllegalArgumentException(
                "HTTP/2 frame size must be between 16384 bytes (16 KiB) and 16777215 bytes (16 MiB)");
        }
        this.frameSize = frameSize;

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
            final MessageEncoding defaultEncoding, final HttpServerStackGrouping transportConfiguration,
            final Uint32 chunkSize, final Uint32 frameSize) {
        this(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis,
            apiRootPath, defaultEncoding, transportConfiguration, chunkSize, frameSize, null);
    }

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis, final String apiRootPath,
            final MessageEncoding defaultEncoding, final HttpServerStackGrouping transportConfiguration,
            final Uint32 chunkSize, final Uint32 frameSize, final String altSvcHeaderValue) {
        this(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis,
            parsePathRootless(apiRootPath), defaultEncoding, transportConfiguration, chunkSize, frameSize,
            altSvcHeaderValue);
    }

    @Beta
    public NettyEndpointConfiguration(final HttpServerStackGrouping transportConfiguration) {
        this(ErrorTagMapping.RFC8040, PrettyPrintParam.TRUE, Uint16.ZERO, Uint32.valueOf(10_000), "restconf",
            MessageEncoding.JSON, transportConfiguration, Uint32.valueOf(262144), Uint32.valueOf(16384), null);
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
    public @NonNull MessageEncoding defaultEncoding() {
        return defaultEncoding;
    }

    /**
     * {@return size of HTTP/1.1 response chunk}
     */
    public Uint32 chunkSize() {
        return chunkSize;
    }

    /**
     * {@return max size of HTTP/2 request frame}
     */
    public Uint32 frameSize() {
        return frameSize;
    }

    public @Nullable String altSvcHeaderValue() {
        return altSvcHeaderValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorTagMapping(), prettyPrint(), sseMaximumFragmentLength(), sseHeartbeatIntervalMillis(),
            apiRootPath, transportConfiguration, defaultEncoding, altSvcHeaderValue);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof NettyEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && apiRootPath.equals(other.apiRootPath) && transportConfiguration.equals(other.transportConfiguration)
            && defaultEncoding.equals(other.defaultEncoding)
            && Objects.equals(altSvcHeaderValue, other.altSvcHeaderValue);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", apiRootPath.stream()
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/")))
            .add("defaultEncoding", defaultEncoding)
            .add("transportConfiguration", transportConfiguration)
            .add("altSvcHeaderValue", altSvcHeaderValue);
    }
}
