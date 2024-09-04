/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
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

    private HTTPClient(final TransportChannelListener listener, final HttpChannelInitializer channelInitializer,
            final RequestDispatcher dispatcher) {
        super(listener, channelInitializer);
        this.dispatcher = dispatcher;
    }

    /**
     * Invokes the HTTP request over established connection.
     *
     * @param request the full http request object
     * @param callback invoked when the request completes
     */
    public void invoke(final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<@NonNull FullHttpResponse> callback) {
        dispatcher.dispatch(request, callback);
    }

    /**
     * Attempt to establish a {@link HTTPClient} by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap Client {@link Bootstrap} to use for the underlying Netty channel
     * @param connectParams Connection parameters
     * @param http2 indicates HTTP/2 protocol to be used
     * @return A future
     * @throws UnsupportedConfigurationException when {@code connectParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ListenableFuture<HTTPClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final HttpClientStackGrouping connectParams, final boolean http2)
            throws UnsupportedConfigurationException {
        final HttpClientGrouping httpParams;
        final TcpClientGrouping tcpParams;
        final TlsClientGrouping tlsParams;
        final var transport = requireNonNull(connectParams).getTransport();
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

        final var dispatcher = http2 ? new ClientHttp2RequestDispatcher() : new ClientHttp1RequestDispatcher();
        final var client = new HTTPClient(listener, new ClientChannelInitializer(httpParams, dispatcher, http2),
            dispatcher);
        final var underlay = tlsParams == null
            ? TCPClient.connect(client.asListener(), bootstrap, tcpParams)
            : TLSClient.connect(client.asListener(), bootstrap, tcpParams, new HttpSslHandlerFactory(tlsParams, http2));
        return transformUnderlay(client, underlay);
    }
}
