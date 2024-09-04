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
import io.netty.handler.codec.http2.Http2ConnectionHandler;
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
 * A {@link HTTPTransportStack} acting as a server.
 */
public abstract sealed class HTTPServer extends HTTPTransportStack permits PlainHTTPServer, TlsHTTPServer {
    public static final String REQUEST_DISPATCHER_HANDLER_NAME = "request-dispatcher";

    private final AuthHandlerFactory authHandlerFactory;

    HTTPServer(final TransportChannelListener listener, final AuthHandlerFactory authHandlerFactory) {
        super(listener);
        this.authHandlerFactory = authHandlerFactory;
    }

    /**
     * Attempt to establish a {@link HTTPServer} on a local address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap {@link ServerBootstrap} to use for the underlying Netty server channel
     * @param listenParams Listening parameters
     * @param dispatcher server logic layer implementation as {@link RequestDispatcher}
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
     * @param dispatcher server logic layer implementation as {@link RequestDispatcher}
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

    private static @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final Tcp tcpCase, final @Nullable AuthHandlerFactory authHandlerFactory)
                throws UnsupportedConfigurationException {
        final var tcp = tcpCase.getTcp();
        final var server = new PlainHTTPServer(listener, authHandlerFactory != null ? authHandlerFactory
            : BasicAuthHandlerFactory.ofNullable(tcp.getHttpServerParameters()));
        return transformUnderlay(server,
            TCPServer.listen(server.asListener(), bootstrap, tcp.nonnullTcpServerParameters()));
    }

    private static @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final Tls tlsCase, final @Nullable AuthHandlerFactory authHandlerFactory)
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
        // External HTTP 2 to internal HTTP 1.1 adapter handler
        initializePipeline(underlayChannel.channel().pipeline(),
            Http2Utils.connectionHandler(true, MAX_HTTP_CONTENT_LENGTH));
        addTransportChannel(new HTTPTransportChannel(underlayChannel));
    }

    abstract void initializePipeline(ChannelPipeline pipeline, Http2ConnectionHandler connectionHandler);

    final void configureEndOfPipeline(final ChannelPipeline pipeline) {
        if (authHandlerFactory != null) {
            pipeline.addLast(authHandlerFactory.create());
        }
    }
}
