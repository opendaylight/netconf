/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.crypto.CMSCertificateParser;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.InlineOrTruststoreCertsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.Inline;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.Quic;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.quic.quic.QuicClientParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.quic.quic.UdpClientParameters;

/**
 * A {@link HTTPTransportStack} acting as a client.
 */
public abstract sealed class HTTPClient extends HTTPTransportStack
        permits PlainHTTPClient, TlsHTTPClient, QuicHTTPClient {
    private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Client");
    private static final AttributeKey<Supplier<ChannelHandler>> AUTH_PROVIDER_FACTORY
        = AttributeKey.valueOf(HTTPClient.class, "authProviderFactory");

    private final @Nullable ClientAuthProvider authProvider;
    private final boolean http2;

    HTTPClient(final TransportChannelListener<? super HTTPTransportChannel> listener, final HTTPScheme scheme,
            final @Nullable ClientAuthProvider authProvider, final boolean http2) {
        super(listener, scheme);
        this.authProvider = authProvider;
        this.http2 = http2;
    }

    /**
     * Attempt to establish a {@link HTTPClient} by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap Client {@link Bootstrap} to use for the underlying Netty channel
     * @param connectParams Connection parameters
     * @param http2 indicates HTTP/2 protocol to be used; ignored when {@code connectParams} specifies the QUIC
     *        transport, which always uses HTTP/3
     * @return A future
     * @throws UnsupportedConfigurationException when {@code connectParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ListenableFuture<HTTPClient> connect(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final Bootstrap bootstrap,
            final HttpClientStackGrouping connectParams, final boolean http2) throws UnsupportedConfigurationException {
        final var transport = requireNonNull(connectParams).getTransport();
        if (transport instanceof Quic quic) {
            // QUIC does not share the tcp/tls transports' underlay-connect-then-attach-codec flow below,
            // so it is handled separately, with its own AUTH_PROVIDER_FACTORY handling inside connectQuic().
            return connectQuic(listener, bootstrap, quic);
        }

        final HttpClientGrouping httpParams;
        final TcpClientGrouping tcpParams;
        final TlsClientGrouping tlsParams;
        switch (transport) {
            case Tcp tcpCase -> {
                final var tcp = tcpCase.getTcp();
                httpParams = tcp.getHttpClientParameters();
                tcpParams = tcp.nonnullTcpClientParameters();
                tlsParams = null;
            }
            case Tls tlsCase -> {
                final var tls = tlsCase.getTls();
                httpParams = tls.getHttpClientParameters();
                tcpParams = tls.nonnullTcpClientParameters();
                tlsParams = tls.nonnullTlsClientParameters();
            }
            default -> throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        }

        bootstrap.attr(AUTH_PROVIDER_FACTORY, () -> ClientAuthProvider.ofNullable(httpParams));
        final HTTPClient client;
        final ListenableFuture<? extends TransportStack> underlay;
        if (tlsParams != null) {
            client = new TlsHTTPClient(listener, ClientAuthProvider.ofNullable(httpParams), http2);
            underlay = TLSClient.connect(client.asListener(), bootstrap, tcpParams,
                new HttpSslHandlerFactory(tlsParams, http2));
        } else {
            client = new PlainHTTPClient(listener, ClientAuthProvider.ofNullable(httpParams), http2);
            underlay = TCPClient.connect(client.asListener(), bootstrap, tcpParams);
        }
        return transformUnderlay(client, underlay);
    }

    /**
     * Attempt to establish a {@link HTTPClient} over QUIC by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap Client {@link Bootstrap} whose {@link EventLoopGroup} is reused for the QUIC datagram channel
     * @param quic QUIC connection parameters
     * @return a future completed with the established {@link HTTPClient}
     * @throws UnsupportedConfigurationException when {@code quic} contains unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    private static ListenableFuture<HTTPClient> connectQuic(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final Bootstrap bootstrap,
            final Quic quic) throws UnsupportedConfigurationException {
        final var quicParams = quic.getQuic();
        if (quicParams == null) {
            throw new UnsupportedConfigurationException("Missing quic parameters");
        }

        final var udpParams = quicParams.nonnullUdpClientParameters();
        final var remoteAddress = remoteAddressOf(udpParams);
        final var localAddress = localAddressOf(udpParams);
        final var trustCertificates = readTrustCertificates(quicParams.nonnullTlsClientParameters());
        final var quicClientParams = requireQuicClientParameters(quicParams.getQuicClientParameters());
        final var httpParams = quicParams.getHttpClientParameters();

        final var sslContext = QuicSslContextBuilder.forClient()
            .trustManager(trustCertificates)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            // FIXME: do not disable host name verification
            // trustCertificates are pinned leaf certs, not a CA chain, so there is no hostname to verify against;
            // same as SslHandlerFactory's tcp/tls equivalent.
            .endpointIdentificationAlgorithm(null)
            .build();
        final var initialMaxStreamData =
            quicClientParams.requireInitialMaxStreamDataBidiRemote().getValue().longValue();
        final var codec = Http3.newQuicClientCodecBuilder()
            .sslContext(sslContext)
            .initialMaxData(quicClientParams.requireInitialMaxData().getValue().longValue())
            // the model only carries a single bidirectional stream flow-control value, apply it in both directions
            .initialMaxStreamDataBidirectionalLocal(initialMaxStreamData)
            .initialMaxStreamDataBidirectionalRemote(initialMaxStreamData)
            .initialMaxStreamsBidirectional(quicClientParams.requireInitialMaxStreamsBidi().longValue())
            .maxIdleTimeout(quicClientParams.requireMaxIdleTimeout().getValue().longValue(), TimeUnit.MILLISECONDS)
            .build();

        final var client = new QuicHTTPClient(listener);
        final var underlayFuture = SettableFuture.<QuicUnderlay>create();
        NettyTransportSupport.newDatagramBootstrap()
            .group(bootstrap.config().group())
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel ch) {
                    ch.pipeline().addLast(codec);
                }
            })
            .bind(localAddress)
            .addListener((ChannelFutureListener) bindFuture -> {
                final var bindCause = bindFuture.cause();
                if (bindCause != null) {
                    underlayFuture.setException(bindCause);
                    return;
                }

                final var datagramChannel = bindFuture.channel();
                QuicChannel.newBootstrap(datagramChannel)
                    .handler(new Http3ClientConnectionHandler())
                    .remoteAddress(remoteAddress)
                    .connect()
                    .addListener((GenericFutureListener<Future<QuicChannel>>) connectFuture -> {
                        final var connectCause = connectFuture.cause();
                        if (connectCause != null) {
                            datagramChannel.close();
                            underlayFuture.setException(connectCause);
                            return;
                        }

                        final var quicChannel = connectFuture.getNow();
                        quicChannel.attr(AUTH_PROVIDER_FACTORY).set(() -> ClientAuthProvider.ofNullable(httpParams));
                        if (underlayFuture.set(new QuicUnderlay(datagramChannel, quicChannel))) {
                            client.addTransportChannel(new HTTPTransportChannel(
                                new QuicTransportChannel(quicChannel), client.scheme()));
                        } else {
                            // connect()'s caller already canceled the returned future: nothing will ever call
                            // QuicUnderlay#shutdown(), so close both channels here to avoid leaking the QUIC
                            // connection and its UDP socket.
                            quicChannel.close();
                            datagramChannel.close();
                        }
                    });
            });
        return transformUnderlay(client, underlayFuture);
    }

    private static @NonNull InetSocketAddress remoteAddressOf(final UdpClientParameters udpParams)
            throws UnsupportedConfigurationException {
        final var host = udpParams.getRemoteAddress();
        final var port = udpParams.getRemotePort();
        if (host == null || port == null) {
            throw new UnsupportedConfigurationException("Missing remote address or port in " + udpParams);
        }
        final var portNumber = port.getValue().toJava();
        final var ipAddress = host.getIpAddress();
        return ipAddress != null ? new InetSocketAddress(IetfInetUtil.inetAddressFor(ipAddress), portNumber)
            : new InetSocketAddress(host.getDomainName().getValue(), portNumber);
    }

    private static @NonNull InetSocketAddress localAddressOf(final UdpClientParameters udpParams) {
        final var localAddress = udpParams.getLocalAddress();
        final var localPort = udpParams.getLocalPort();
        final var portNumber = localPort == null ? 0 : localPort.getValue().toJava();
        return localAddress == null ? new InetSocketAddress(portNumber)
            : new InetSocketAddress(IetfInetUtil.inetAddressFor(localAddress), portNumber);
    }

    private static X509Certificate @NonNull [] readTrustCertificates(final TlsClientGrouping tlsParams)
            throws UnsupportedConfigurationException {
        final var serverAuth = tlsParams.getServerAuthentication();
        if (serverAuth == null) {
            throw new UnsupportedConfigurationException("Missing TLS server authentication");
        }
        if (serverAuth.getRawPublicKeys() != null) {
            throw new UnsupportedConfigurationException("Raw public key server authentication is not supported");
        }

        final var certificates = new ArrayList<X509Certificate>();
        collectTrustCertificates(certificates, serverAuth.getCaCerts());
        collectTrustCertificates(certificates, serverAuth.getEeCerts());
        if (certificates.isEmpty()) {
            throw new UnsupportedConfigurationException("No trust anchor certificates in " + serverAuth);
        }
        return certificates.toArray(new X509Certificate[0]);
    }

    private static void collectTrustCertificates(final @NonNull List<X509Certificate> certificates,
            final @Nullable InlineOrTruststoreCertsGrouping certs) throws UnsupportedConfigurationException {
        if (certs == null) {
            return;
        }

        final var inlineOrTruststore = certs.getInlineOrTruststore();
        if (!(inlineOrTruststore instanceof Inline inline)) {
            throw new UnsupportedConfigurationException("Unsupported trust certificates storage "
                + inlineOrTruststore);
        }
        final var inlineDefinition = inline.getInlineDefinition();
        if (inlineDefinition == null) {
            throw new UnsupportedConfigurationException("Missing inline trust certificates definition");
        }
        for (var certificate : inlineDefinition.nonnullCertificate().values()) {
            certificates.add((X509Certificate) CMSCertificateParser.parseCertificate(certificate.requireCertData()));
        }
    }

    private static @NonNull QuicClientParameters requireQuicClientParameters(
            final @Nullable QuicClientParameters parameters) throws UnsupportedConfigurationException {
        if (parameters == null) {
            throw new UnsupportedConfigurationException("Missing quic-client-parameters");
        }
        if (parameters.getInitialMaxData() == null || parameters.getInitialMaxStreamDataBidiRemote() == null
                || parameters.getInitialMaxStreamsBidi() == null || parameters.getMaxIdleTimeout() == null) {
            throw new UnsupportedConfigurationException("Incomplete quic-client-parameters");
        }
        return parameters;
    }

    /**
     * Retrieves the authentication provider factory associated with the given channel.
     *
     * <p>In a multiplexed HTTP/2 environment, child streams share a single parent channel.
     * Each concurrent child stream must generate its own isolated instance. This factory,
     * stored in the parent channel's attributes, allows child channels to safely manufacture
     * their own authentication handlers without interfering with other concurrent requests.
     *
     * @param channel the parent Netty {@link Channel} containing the authentication factory attribute
     * @return a {@link Supplier} capable of generating new {@link ClientAuthProvider} instances,
     *         or {@code null} if authentication is not configured for this connection
     */
    public static Supplier<ChannelHandler> getAuthFactory(final Channel channel) {
        return channel.attr(AUTH_PROVIDER_FACTORY).get();
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var pipeline = underlayChannel.channel().pipeline();
        if (http2) {
            final var frameCodec = Http2FrameCodecBuilder.forClient()
                .frameLogger(FRAME_LOGGER)
                .gracefulShutdownTimeoutMillis(0L)
                .build();
            initializePipeline(underlayChannel, pipeline, frameCodec);
        } else {
            // HTTP 1.1
            pipeline.addLast(new HttpClientCodec(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
            configureEndOfPipeline(underlayChannel, pipeline);
        }
    }

    final void configureEndOfPipeline(final TransportChannel underlayChannel, final ChannelPipeline pipeline) {
        if (http2) {
            pipeline.addLast("h2-multiplexer", new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
        } else {
            if (authProvider != null) {
                pipeline.addLast(authProvider);
            }
        }

        // signal client transport is ready to send requests
        // NB. while server signals readiness on exit from initChannel(),
        // client needs additional confirmation for upgrade completion in case of HTTP/2 cleartext flow
        addTransportChannel(new HTTPTransportChannel(underlayChannel, scheme()));
    }

    abstract void initializePipeline(TransportChannel underlayChannel, ChannelPipeline pipeline,
            Http2ConnectionHandler connectionHandler);
}
