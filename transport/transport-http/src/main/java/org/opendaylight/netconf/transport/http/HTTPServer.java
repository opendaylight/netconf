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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.transport.Tls;

/**
 * A {@link HTTPTransportStack} acting as a server. When this stack is set up, {@link HTTPTransportChannel}s reported
 * to the {@link TransportChannelListener} are set up such they support both HTTP/1.1 and HTTP/2. The details of the
 * setup are subject to change and users are advised to attach their own subclass of {@link HTTPServerSession} as the
 * last handler. Doing so will provide a predictable API surface as to how the pipeline is set up.
 */
public abstract sealed class HTTPServer extends HTTPTransportStack permits PlainHTTPServer, TlsHTTPServer {
    private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Server");

    private final AuthHandlerFactory authHandlerFactory;

    HTTPServer(final TransportChannelListener<? super HTTPTransportChannel> listener, final HTTPScheme scheme,
            final AuthHandlerFactory authHandlerFactory) {
        super(listener, scheme);
        this.authHandlerFactory = authHandlerFactory;
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
    public static final @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final HttpServerStackGrouping listenParams)
                throws UnsupportedConfigurationException {
        return listen(listener, bootstrap, listenParams, null);
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
    public static final @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final HttpServerStackGrouping listenParams,
            final @Nullable AuthHandlerFactory authHandlerFactory) throws UnsupportedConfigurationException {
        final var transport = requireNonNull(listenParams).getTransport();
        return switch (transport) {
            case Tcp tcpCase -> listen(listener, bootstrap, tcpCase, authHandlerFactory);
            case Tls tlsCase -> listen(listener, bootstrap, tlsCase, authHandlerFactory);
            default -> throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        };
    }

    private static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final Tcp tcpCase, final @Nullable AuthHandlerFactory authHandlerFactory)
                throws UnsupportedConfigurationException {
        final var tcp = tcpCase.getTcp();
        final var server = new PlainHTTPServer(listener, authHandlerFactory != null ? authHandlerFactory
            : BasicAuthHandlerFactory.ofNullable(tcp.getHttpServerParameters()));
        return transformUnderlay(server,
            TCPServer.listen(server.asListener(), bootstrap, tcp.nonnullTcpServerParameters()));
    }

    private static @NonNull ListenableFuture<HTTPServer> listen(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final ServerBootstrap bootstrap,
            final Tls tlsCase, final @Nullable AuthHandlerFactory authHandlerFactory)
                throws UnsupportedConfigurationException {
        final var tls = tlsCase.getTls();
        final var server = new TlsHTTPServer(listener, authHandlerFactory != null ? authHandlerFactory
            : BasicAuthHandlerFactory.ofNullable(tls.getHttpServerParameters()));
        return transformUnderlay(server,
            TLSServer.listen(server.asListener(), bootstrap, tls.nonnullTcpServerParameters(),
                new HttpSslHandlerFactory(tls.nonnullTlsServerParameters())));
    }

    @Override
    protected final void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var pipeline = underlayChannel.channel().pipeline();

        // External HTTP 2 to internal HTTP 1.1 adapter handler
        final var connection = new DefaultHttp2Connection(true);
        final var frameListener = new InboundHttp2ToHttpAdapterBuilder(connection)
            .maxContentLength(MAX_HTTP_CONTENT_LENGTH)
            .propagateSettings(true)
            .build();

        initializePipeline(pipeline, new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(new DelegatingDecompressorFrameListener(connection, frameListener))
            .connection(connection)
            .frameLogger(FRAME_LOGGER)
            .gracefulShutdownTimeoutMillis(0L)
            .build());

        if (authHandlerFactory != null) {
            pipeline.addLast(authHandlerFactory.create());
        }

        addTransportChannel(new HTTPTransportChannel(underlayChannel, scheme()));
    }

    abstract void initializePipeline(ChannelPipeline pipeline, Http2ConnectionHandler connectionHandler);
}
