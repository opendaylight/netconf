/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.HttpServerListenStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.OdlServerLimitsGrouping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.ServerLimitsGrouping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.odl.http.server.listen.stack.grouping.LimitsUnderHttpTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.odl.http.server.listen.stack.grouping.LimitsUnderHttpTls;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Limits an HTTP server applies to an inbound request, and the Netty knobs backing them. This is the Java-side
 * projection of the {@code server-limits} and {@code odl-server-limits} containers {@code odl-http-server} augments
 * into the {@code http-server-parameters} of the TCP and TLS transport cases.
 *
 * @param maxInitialLineLength maximum length of an HTTP/1.1 request line, in bytes. A request exceeding it is rejected
 *        with {@code 414 URI Too Long}.
 * @param maxHeaderSize maximum size of an HTTP/1.1 header section, in bytes. A request exceeding it is rejected with
 *        {@code 431 Request Header Fields Too Large}.
 * @param maxRequestChunkSize maximum size of a single {@code HttpContent} emitted by the HTTP/1.1 request decoder, in
 *        bytes. This does not limit the request body, only how much of it is handed to the pipeline at a time.
 * @param maxRequestBodySize maximum size of an aggregated request body, in bytes. A request exceeding it is rejected
 *        with {@code 413 Content Too Large}. Applies to HTTP/1.1, HTTP/2 and HTTP/3 alike.
 * @param maxFrameSize maximum HTTP/2 frame payload size advertised as {@code SETTINGS_MAX_FRAME_SIZE}, in bytes
 */
@NonNullByDefault
public record HTTPServerLimits(
        int maxInitialLineLength,
        int maxHeaderSize,
        int maxRequestChunkSize,
        int maxRequestBodySize,
        int maxFrameSize) {
    /**
     * Minimum {@code SETTINGS_MAX_FRAME_SIZE}, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9113#section-6.5.2">RFC9113</a>.
     */
    public static final int MIN_FRAME_SIZE = 16384;
    /**
     * Maximum {@code SETTINGS_MAX_FRAME_SIZE}, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9113#section-6.5.2">RFC9113</a>.
     */
    public static final int MAX_FRAME_SIZE = 16777215;

    /**
     * Default limits, matching the {@code default} substatements of the YANG model. The body limit is deliberately
     * generous, as RESTCONF requests routinely carry an entire subtree.
     */
    public static final HTTPServerLimits DEFAULT = new HTTPServerLimits(8192, 16384, 8192, 10485760, MIN_FRAME_SIZE);

    public HTTPServerLimits {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        checkPositive(maxHeaderSize, "maxHeaderSize");
        checkPositive(maxRequestChunkSize, "maxRequestChunkSize");
        checkPositive(maxRequestBodySize, "maxRequestBodySize");
        if (maxFrameSize < MIN_FRAME_SIZE || maxFrameSize > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("maxFrameSize must be between " + MIN_FRAME_SIZE + " and "
                + MAX_FRAME_SIZE);
        }
    }

    /**
     * Extract the limits configured for a listen stack. Any part of the configuration which is absent contributes its
     * {@link #DEFAULT} counterpart, mirroring how a YANG {@code default} applies to an unset leaf.
     *
     * @param listenParams the listen stack configuration
     * @return the limits to apply
     */
    public static HTTPServerLimits of(final HttpServerListenStackGrouping listenParams) {
        return switch (listenParams.getTransport()) {
            case HttpOverTcp tcpCase -> {
                final var tcp = tcpCase.getHttpOverTcp();
                final var params = tcp == null ? null : tcp.getHttpServerParameters();
                yield params == null ? DEFAULT : of(params.augmentation(LimitsUnderHttpTcp.class));
            }
            case HttpOverTls tlsCase -> {
                final var tls = tlsCase.getHttpOverTls();
                final var params = tls == null ? null : tls.getHttpServerParameters();
                yield params == null ? DEFAULT : of(params.augmentation(LimitsUnderHttpTls.class));
            }
            // http-over-quic carries no limits of its own: an HTTP/3 listener shares the limits of the endpoint it
            // advertises itself from
            case null, default -> DEFAULT;
        };
    }

    private static <T extends ServerLimitsGrouping & OdlServerLimitsGrouping> HTTPServerLimits of(
            final @Nullable T augmentation) {
        if (augmentation == null) {
            return DEFAULT;
        }
        final var limits = augmentation.getServerLimits();
        final var odlLimits = augmentation.getOdlServerLimits();
        return new HTTPServerLimits(
            intOrDefault(limits == null ? null : limits.getMaxInitialLineLength(), DEFAULT.maxInitialLineLength),
            intOrDefault(limits == null ? null : limits.getMaxHeaderSize(), DEFAULT.maxHeaderSize),
            intOrDefault(odlLimits == null ? null : odlLimits.getMaxRequestChunkSize(), DEFAULT.maxRequestChunkSize),
            intOrDefault(limits == null ? null : limits.getMaxRequestBodySize(), DEFAULT.maxRequestBodySize),
            intOrDefault(limits == null ? null : limits.getMaxFrameSize(), DEFAULT.maxFrameSize));
    }

    /**
     * {@return a copy of these limits with {@link #maxFrameSize()} replaced}
     *
     * @param newMaxFrameSize the new {@code SETTINGS_MAX_FRAME_SIZE}
     */
    public HTTPServerLimits withMaxFrameSize(final int newMaxFrameSize) {
        return newMaxFrameSize == maxFrameSize ? this
            : new HTTPServerLimits(maxInitialLineLength, maxHeaderSize, maxRequestChunkSize, maxRequestBodySize,
                newMaxFrameSize);
    }

    private static int intOrDefault(final @Nullable Uint32 value, final int defaultValue) {
        return value == null ? defaultValue : value.intValue();
    }

    private static void checkPositive(final int value, final String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1");
        }
    }
}
