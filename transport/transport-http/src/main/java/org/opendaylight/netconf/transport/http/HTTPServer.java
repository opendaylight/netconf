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
import io.netty.channel.ChannelHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.http.server.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.http.server.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev231228.TlsServerGrouping;

public final class HTTPServer extends HTTPTransportStack {

    private HTTPServer(final TransportChannelListener listener, final ChannelHandler handler) {
        super(listener, handler);
    }

    public static @NonNull ListenableFuture<HTTPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final HttpServerStackGrouping listenParams,
            final RequestDispatcher dispatcher) throws UnsupportedConfigurationException {
        final HttpServerGrouping httpParams;
        final TcpServerGrouping tcpParams;
        final TlsServerGrouping tlsParams;
        final var transport = requireNonNull(listenParams).getTransport();
        if (transport instanceof Tcp tcp) {
            httpParams = tcp.getTcp().nonnullHttpServerParameters();
            tcpParams = tcp.getTcp().nonnullTcpServerParameters();
            tlsParams = null;
        } else if (transport instanceof Tls tls) {
            httpParams = tls.getTls().nonnullHttpServerParameters();
            tcpParams = tls.getTls().nonnullTcpServerParameters();
            tlsParams = tls.getTls().nonnullTlsServerParameters();
        } else {
            throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        }
        final var server = new HTTPServer(listener,
            new ServerChannelInitializer(httpParams, requireNonNull(dispatcher)));
        final var underlay = tlsParams == null
            ? TCPServer.listen(server.asListener(), bootstrap, tcpParams)
            : TLSServer.listen(server.asListener(), bootstrap, tcpParams, new FixedSslHandlerFactory(tlsParams));
        return transformUnderlay(server, underlay);
    }
}
