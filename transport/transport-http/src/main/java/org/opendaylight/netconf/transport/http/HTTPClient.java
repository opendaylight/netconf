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
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev240208.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.TlsClientGrouping;

public final class HTTPClient extends HTTPTransportStack {

    private final RequestDispatcher dispatcher;

    private HTTPClient(final TransportChannelListener listener, final ChannelHandler handler,
            final RequestDispatcher dispatcher) {
        super(listener, handler);
        this.dispatcher = dispatcher;
    }

    public ListenableFuture<FullHttpResponse> invoke(final FullHttpRequest request) {
        return dispatcher.dispatch(requireNonNull(request));
    }

    public static ListenableFuture<HTTPClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final HttpClientStackGrouping connectParams)
                throws UnsupportedConfigurationException {
        final HttpClientGrouping httpParams;
        final TcpClientGrouping tcpParams;
        final TlsClientGrouping tlsParams;
        final var transport = requireNonNull(connectParams).getTransport();
        if (transport instanceof Tcp tcp) {
            httpParams = tcp.getTcp().getHttpClientParameters();
            tcpParams = tcp.getTcp().nonnullTcpClientParameters();
            tlsParams = null;
        } else if (transport instanceof Tls tls) {
            httpParams = tls.getTls().getHttpClientParameters();
            tcpParams = tls.getTls().nonnullTcpClientParameters();
            tlsParams = tls.getTls().nonnullTlsClientParameters();
        } else {
            throw new UnsupportedConfigurationException("Unsupported transport: " + transport);
        }
        final var dispatcher = new ClientRequestDispatcher();
        final var client = new HTTPClient(listener, new ClientChannelInitializer(httpParams, dispatcher), dispatcher);
        final var underlay = tlsParams == null
            ? TCPClient.connect(client.asListener(), bootstrap, tcpParams)
            : TLSClient.connect(client.asListener(), bootstrap, tcpParams, new FixedSslHandlerFactory(tlsParams));
        return transformUnderlay(client, underlay);
    }
}
