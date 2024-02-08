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

/**
 * A {@link HTTPTransportStack} acting as a client.
 */
public final class HTTPClient extends HTTPTransportStack {

    private final RequestDispatcher dispatcher;

    private HTTPClient(final TransportChannelListener listener, final ChannelHandler handler,
            final RequestDispatcher dispatcher) {
        super(listener, handler);
        this.dispatcher = dispatcher;
    }

    /**
     * Invokes the HTTP request over established connection.
     *
     * @param request the full http request object
     * @return a future providing full http response or cause in case of error
     */
    public ListenableFuture<FullHttpResponse> invoke(final FullHttpRequest request) {
        return dispatcher.dispatch(requireNonNull(request));
    }

    /**
     * Attempt to establish a {@link HTTPClient} by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap Client {@link Bootstrap} to use for the underlying Netty channel
     * @param connectParams Connection parameters
     * @return A future
     * @throws UnsupportedConfigurationException when {@code connectParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
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
