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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev240208.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev240208.TlsServerGrouping;

/**
 * A {@link HTTPTransportStack} acting as a server.
 */
public final class HTTPServer extends HTTPTransportStack {

    private HTTPServer(final TransportChannelListener listener, final HttpChannelInitializer channelInitializer) {
        super(listener, channelInitializer);
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
    public static @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final HttpServerStackGrouping listenParams,
            final RequestDispatcher dispatcher) throws UnsupportedConfigurationException {
        return listen(listener, bootstrap, listenParams, dispatcher, null);
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
    public static @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final HttpServerStackGrouping listenParams,
            final RequestDispatcher dispatcher, final @Nullable AuthHandlerFactory authHandlerFactory)
            throws UnsupportedConfigurationException {
        final HttpServerGrouping httpParams;
        final TcpServerGrouping tcpParams;
        final TlsServerGrouping tlsParams;
        final var transport = requireNonNull(listenParams).getTransport();
        switch (transport) {
            case Tcp tcpCase -> {
                final var tcp = tcpCase.getTcp();
                httpParams = tcp.getHttpServerParameters();
                tcpParams = tcp.nonnullTcpServerParameters();
                tlsParams = null;
            }
            case Tls tlsCase -> {
                final var tls = tlsCase.getTls();
                httpParams = tls.getHttpServerParameters();
                tcpParams = tls.nonnullTcpServerParameters();
                tlsParams = tls.nonnullTlsServerParameters();
            }
            default -> throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        }

        final var channelInitializer = authHandlerFactory == null
            ? new ServerChannelInitializer(httpParams, requireNonNull(dispatcher))
            : new ServerChannelInitializer(authHandlerFactory, requireNonNull(dispatcher));
        final var server = new HTTPServer(listener, channelInitializer);
        final var underlay = tlsParams == null
            ? TCPServer.listen(server.asListener(), bootstrap, tcpParams)
            : TLSServer.listen(server.asListener(), bootstrap, tcpParams, new HttpSslHandlerFactory(tlsParams));
        return transformUnderlay(server, underlay);
    }
}
