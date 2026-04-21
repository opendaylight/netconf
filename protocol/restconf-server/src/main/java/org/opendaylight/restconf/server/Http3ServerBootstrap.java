/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.netconf.transport.crypto.KeyPairParser;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverQuic;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.Inline;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.server.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.server.rev251216.udp.server.LocalBind;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.QuicUnderHttp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.quic.under.http.QuicServerParameters;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class Http3ServerBootstrap implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Http3ServerBootstrap.class);
    private static final int MAX_HTTP3_CONTENT_LENGTH = 16 * 1024;
    private static final AttributeKey<Uint32> MAX_CHUNK_SIZE = AttributeKey.valueOf(Http3ServerBootstrap.class,
        "maxChunkSize");

    private final Channel channel;
    private final EventLoopGroup quicGroup;

    private Http3ServerBootstrap(final Channel channel, final EventLoopGroup quicGroup) {
        this.channel = requireNonNull(channel);
        this.quicGroup = requireNonNull(quicGroup);
    }

    static Http3ServerBootstrap start(final HttpOverQuic transport, final EndpointRoot root, final Uint32 chunkSize,
            final WriteBufferWaterMark writeBufferWaterMark) throws SSLException, UnsupportedConfigurationException {
        final var httpOverQuic = transport.getHttpOverQuic();
        if (httpOverQuic == null) {
            throw new UnsupportedConfigurationException("Missing http-over-quic parameters");
        }

        final var bind = firstLocalBind(httpOverQuic.nonnullUdpServerParameters().nonnullLocalBind().values());
        if (bind == null) {
            throw new UnsupportedConfigurationException("Missing UDP bind point");
        }

        final var certificateKey = readCertificateKey(httpOverQuic.nonnullTlsServerParameters());
        final var sslContext = QuicSslContextBuilder.forServer(certificateKey.privateKey(), null,
                certificateKey.certificate())
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        final var streamInitializer = new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(final QuicStreamChannel stream) {
                stream.config().setWriteBufferWaterMark(writeBufferWaterMark);
                final var pipeline = stream.pipeline();
                final var maxChunkSize = stream.parent().attr(MAX_CHUNK_SIZE).get();
                pipeline.addLast("h3-stream-log", new LoggingHandler(LogLevel.DEBUG));
                pipeline.addLast(new Http3FrameToHttpObjectCodec(true));
                pipeline.addLast(new HttpObjectAggregator(MAX_HTTP3_CONTENT_LENGTH));
                pipeline.addLast("restconf-session", new ConcurrentRestconfSession(HTTPScheme.HTTPS,
                    requireNonNull(stream.parent().remoteAddress()), root, maxChunkSize));
            }
        };

        final var quicServerParameters = requireQuicServerParameters(transport);
        final var codec = Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .initialMaxData(quicServerParameters.requireInitialMaxData().getValue().longValue())
            .initialMaxStreamDataBidirectionalRemote(
                quicServerParameters.requireInitialMaxStreamDataBidiRemote().getValue().longValue())
            .initialMaxStreamsBidirectional(quicServerParameters.requireInitialMaxStreamsBidi().longValue())
            .handler(new ChannelInitializer<QuicChannel>() {
                @Override
                protected void initChannel(final QuicChannel ch) {
                    ch.attr(MAX_CHUNK_SIZE).set(maxChunkSize(ch.peerTransportParameters(), chunkSize));
                    ch.pipeline().addLast(new Http3ServerConnectionHandler(streamInitializer));
                }
            })
            .build();

        final var group = NettyTransportSupport.newEventLoopGroup(0,
            Thread.ofPlatform()
                .name("restconf-http3-", 0)
                .uncaughtExceptionHandler((thread, ex) ->
                    LOG.error("Thread terminated due to uncaught exception: {}", thread.getName(), ex))
                .factory());

        final var bootstrap = NettyTransportSupport.newDatagramBootstrap()
            .group(group)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel ch) {
                    ch.pipeline().addLast(new LoggingHandler("http3-udp", LogLevel.DEBUG));
                    ch.pipeline().addLast(codec);
                }
            });

        final var bindAddress = bind.requireLocalAddress().stringValue();
        final var bindPort = bind.requireLocalPort().getValue().toJava();

        final var channel = bootstrap
            .bind(new InetSocketAddress(bindAddress, bindPort))
            .syncUninterruptibly()
            .channel();

        LOG.info("HTTP/3 listener bound on {}:{}", bindAddress, bindPort);
        return new Http3ServerBootstrap(channel, group);
    }

    @Override
    public void close() {
        quicGroup.shutdownGracefully();
        channel.close().syncUninterruptibly();
    }

    private static @Nullable LocalBind firstLocalBind(final Collection<LocalBind> localBinds) {
        if (localBinds.isEmpty()) {
            return null;
        }
        final var first = localBinds.iterator().next();
        final var size = localBinds.size();
        if (size > 1) {
            LOG.warn("HTTP/3 transport has {} UDP bind points, using {}", size, first);
        }
        return first;
    }

    private static CertificateKey readCertificateKey(final TlsServerGrouping tlsServerParameters)
            throws UnsupportedConfigurationException {
        final var authType = tlsServerParameters.nonnullServerIdentity().getAuthType();
        if (!(authType instanceof Certificate certificateAuth)) {
            throw new UnsupportedConfigurationException("Unsupported TLS server identity " + authType);
        }

        final var inlineOrKeystore = certificateAuth.nonnullCertificate().getInlineOrKeystore();
        if (!(inlineOrKeystore instanceof Inline inline)) {
            throw new UnsupportedConfigurationException("Unsupported TLS key storage " + inlineOrKeystore);
        }

        final var inlineDefinition = inline.getInlineDefinition();
        if (inlineDefinition == null) {
            throw new UnsupportedConfigurationException("Missing inline TLS definition");
        }

        final var keyPair = KeyPairParser.parseKeyPair(inlineDefinition);
        final var certificate = (X509Certificate) CMSCertificateParser.parseCertificate(inlineDefinition
            .requireCertData());
        if (!keyPair.getPublic().equals(certificate.getPublicKey())) {
            throw new UnsupportedConfigurationException("TLS private key mismatches certificate public key");
        }
        return new CertificateKey(certificate, keyPair.getPrivate());
    }

    private static QuicServerParameters requireQuicServerParameters(final HttpOverQuic transport)
            throws UnsupportedConfigurationException {
        final var quic = transport.augmentation(QuicUnderHttp.class);
        if (quic == null || quic.getQuicServerParameters() == null) {
            throw new UnsupportedConfigurationException("Missing quic-server-parameters augmentation");
        }

        final var parameters = quic.getQuicServerParameters();
        if (parameters.getInitialMaxData() == null || parameters.getInitialMaxStreamDataBidiRemote() == null
            || parameters.getInitialMaxStreamsBidi() == null) {
            throw new UnsupportedConfigurationException("Incomplete quic-server-parameters augmentation");
        }
        return parameters;
    }

    /**
     * Compute an effective maximum response chunk size for an HTTP/3 connection.
     *
     * <p>The peer-advertised {@code max_udp_payload_size} applies to the UDP payload on the wire, while
     * {@code chunkSize} represents application payload bytes. A fixed overhead of 64 bytes is subtracted to leave
     * headroom for QUIC/HTTP/3 framing (varint-encoded headers, STREAM/DATA framing) and the AEAD tag, plus
     * occasional coalesced control frames.
     *
     * <p>The resulting bound is clamped to a minimum of 256 bytes to avoid pathological tiny chunks when a peer
     * advertises a very small {@code max_udp_payload_size}, which would otherwise amplify per-chunk overhead and
     * increase flush pressure.
     *
     * @param parameters peer QUIC transport parameters, or {@code null} if unavailable
     * @param chunkSize configured response chunk size (application payload bytes)
     * @return effective maximum chunk size for this connection
     */
    private static Uint32 maxChunkSize(final @Nullable QuicTransportParameters parameters, final Uint32 chunkSize) {
        final var overhead = 64;
        final var peerMaxUdp = parameters != null ? parameters.maxUdpPayloadSize() : Long.MAX_VALUE;
        final var peerBound = peerMaxUdp == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(256L, peerMaxUdp - overhead);
        return Uint32.valueOf(Math.min(chunkSize.longValue(), peerBound));
    }

    private record CertificateKey(X509Certificate certificate, PrivateKey privateKey) {
    }
}
