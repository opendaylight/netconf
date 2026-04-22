/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.netconf.transport.crypto.KeyPairParser;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.HttpServerListenStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverQuic;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.Inline;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.TlsServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.server.identity.auth.type.Certificate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.server.rev251216.udp.server.LocalBind;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.QuicUnderHttp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.quic.under.http.QuicServerParameters;
import org.opendaylight.yangtools.yang.common.Empty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link HTTPTransportStack} acting as a server. When this stack is set up, {@link HTTPTransportChannel}s reported
 * to the {@link TransportChannelListener} have on their transport, but not their definite object and execution models,
 * set up. Downstream handler should watch for {@link HTTPServerPipelineSetup} user event and attach the appropriate
 * handler to handle the setup. {@link HTTPServerSessionBootstrap} provides a baseline class, and reference
 * implementation, for the appropriate handler.
 */
public final class HTTPServer extends HTTPTransportStack {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPServer.class);

    HTTPServer(final TransportChannelListener<? super HTTPTransportChannel> listener, final HTTPScheme scheme) {
        super(listener, scheme);
    }

    /**
     * Attempt to establish a {@link HTTPServer} on a local address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap {@link ServerBootstrap} to use for the underlying Netty server channel
     * @param listenParams Listening parameters
     * @return A future
     * @throws UnsupportedConfigurationException when {@code listenParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final HttpServerListenStackGrouping listenParams) throws UnsupportedConfigurationException {
        return listen(listener, bootstrap, listenParams, null);
    }

    /**
     * Attempt to establish a {@link HTTPServer} over QUIC on a local address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param quicCase QUIC listening parameters
     * @return A future
     * @throws UnsupportedConfigurationException when {@code quicCase} contains unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final HttpOverQuic quicCase)
            throws UnsupportedConfigurationException {
        return listenQuic(listener, requireNonNull(quicCase));
    }

    /**
     * Attempt to establish a {@link HTTPServer} on a local address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap {@link ServerBootstrap} to use for the underlying Netty server channel
     * @param listenParams Listening parameters
     * @param authHandlerFactory {@link AuthHandlerFactory} instance, provides channel handler serving the request
     *      authentication; optional, if defined the Basic Auth settings of listenParams will be ignored
     * @return A future
     * @throws UnsupportedConfigurationException when {@code listenParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final HttpServerListenStackGrouping listenParams, final @Nullable AuthHandlerFactory authHandlerFactory)
                throws UnsupportedConfigurationException {
        final var transport = requireNonNull(listenParams).getTransport();
        return switch (transport) {
            case HttpOverTcp tcpCase -> listen(listener, bootstrap, tcpCase);
            case HttpOverTls tlsCase -> listen(listener, bootstrap, tlsCase);
            case HttpOverQuic quicCase -> listenQuic(listener, quicCase);
            default -> throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        };
    }

    private static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final HttpOverTcp tcpCase) throws UnsupportedConfigurationException {
        final var tcp = tcpCase.getHttpOverTcp();
        final var server = new HTTPServer(listener, HTTPScheme.HTTP);
        return transformUnderlay(server,
            TCPServer.listen(server.asListener(), bootstrap, tcp.nonnullTcpServerParameters()));
    }

    private static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final HttpOverTls tlsCase) throws UnsupportedConfigurationException {
        final var tls = tlsCase.getHttpOverTls();
        final var server = new HTTPServer(listener, HTTPScheme.HTTPS);
        return transformUnderlay(server,
            TLSServer.listen(server.asListener(), bootstrap, tls.nonnullTcpServerParameters(),
                new HttpSslHandlerFactory(tls.nonnullTlsServerParameters())));
    }

    private static @NonNull ListenableFuture<HTTPServer> listenQuic(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final HttpOverQuic quicCase)
            throws UnsupportedConfigurationException {
        final var quic = quicCase.getHttpOverQuic();
        if (quic == null) {
            throw new UnsupportedConfigurationException("Missing http-over-quic parameters");
        }

        final var bind = firstLocalBind(quic.nonnullUdpServerParameters().nonnullLocalBind().values());
        if (bind == null) {
            throw new UnsupportedConfigurationException("Missing UDP bind point");
        }

        final var certificateKey = readCertificateKey(quic.nonnullTlsServerParameters());
        final var quicServerParameters = requireQuicServerParameters(quicCase);
        final var bindAddress = bind.requireLocalAddress().stringValue();
        final var bindPort = bind.requireLocalPort().getValue().toJava();

        final var server = new HTTPServer(listener, HTTPScheme.HTTPS);
        final var group = NettyTransportSupport.newEventLoopGroup(0,
            Thread.ofPlatform().name("transport-http3-", 0).factory());
        final var codec = buildQuicCodec(server, certificateKey, quicServerParameters);
        final var channel = createChannel(group, codec, bindAddress, bindPort);
        return transformUnderlay(server, Futures.immediateFuture(new QuicUnderlay(channel, group)));
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        addTransportChannel(new HTTPTransportChannel(underlayChannel, scheme()));
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

    private static ChannelHandler buildQuicCodec(final HTTPServer server, final CertificateKey certificateKey,
            final QuicServerParameters quicServerParameters) {
        final var sslContext = QuicSslContextBuilder.forServer(certificateKey.privateKey(), null,
                certificateKey.certificate())
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();
        return Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .initialMaxData(quicServerParameters.requireInitialMaxData().getValue().longValue())
            .initialMaxStreamDataBidirectionalRemote(
                quicServerParameters.requireInitialMaxStreamDataBidiRemote().getValue().longValue())
            .initialMaxStreamsBidirectional(quicServerParameters.requireInitialMaxStreamsBidi().longValue())
            .handler(new ChannelInitializer<QuicChannel>() {
                @Override
                protected void initChannel(final QuicChannel ch) {
                    server.addTransportChannel(new HTTPTransportChannel(new QuicTransportChannel(ch), server.scheme()));
                }
            })
            .build();
    }

    private static Channel createChannel(final EventLoopGroup group, final ChannelHandler codec,
            final String bindAddress, final int bindPort) {
        return NettyTransportSupport.newDatagramBootstrap()
            .group(group)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel ch) {
                    ch.pipeline().addLast(codec);
                }
            })
            .bind(new InetSocketAddress(bindAddress, bindPort))
            .syncUninterruptibly()
            .channel();
    }

    private record CertificateKey(X509Certificate certificate, PrivateKey privateKey) {
    }

    private record QuicUnderlay(Channel channel, EventLoopGroup quicGroup) implements TransportStack {
        @Override
        public @NonNull ListenableFuture<Empty> shutdown() {
            return Futures.whenAllSucceed(HTTPServer.toListenableFuture(channel.close()),
                HTTPServer.toListenableFuture(quicGroup.shutdownGracefully()))
                .call(Empty::value, MoreExecutors.directExecutor());
        }
    }
}
