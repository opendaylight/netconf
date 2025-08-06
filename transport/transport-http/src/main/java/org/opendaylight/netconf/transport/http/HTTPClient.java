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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.logging.LogLevel;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.TlsClientGrouping;

/**
 * A {@link HTTPTransportStack} acting as a client.
 */
public abstract sealed class HTTPClient extends HTTPTransportStack permits PlainHTTPClient, TlsHTTPClient {
    private static final Http2FrameLogger FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, "Client");

    private final ClientRequestDispatcher dispatcher;
    private final ClientAuthProvider authProvider;
    private final boolean http2;

    HTTPClient(final TransportChannelListener<? super HTTPTransportChannel> listener, final HTTPScheme scheme,
            final ClientAuthProvider authProvider, final boolean http2) {
        super(listener, scheme);
        this.authProvider = authProvider;
        this.http2 = http2;
        dispatcher = http2 ? new ClientHttp2RequestDispatcher(scheme) : new ClientHttp1RequestDispatcher();
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
    public static ListenableFuture<HTTPClient> connect(
            final TransportChannelListener<? super HTTPTransportChannel> listener, final Bootstrap bootstrap,
            final HttpClientStackGrouping connectParams, final boolean http2) throws UnsupportedConfigurationException {
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

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var pipeline = underlayChannel.channel().pipeline();
        if (http2) {
            // External HTTP 2 to internal HTTP 1.1 adapter handler
            final var connection = new DefaultHttp2Connection(false);

            initializePipeline(underlayChannel, pipeline, new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(new DelegatingDecompressorFrameListener(connection,
                    Http2ToHttpAdapter.builder(connection).maxContentLength(MAX_HTTP_CONTENT_LENGTH).build(),
                    // FIXME: allow for maxAllocation control to prevent OutOfMemoryError
                    0))
                .connection(connection)
                .frameLogger(FRAME_LOGGER)
                .gracefulShutdownTimeoutMillis(0L)
                .build());
        } else {
            // HTTP 1.1
            pipeline.addLast(new HttpClientCodec(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
            configureEndOfPipeline(underlayChannel, pipeline);
        }
    }

    final void configureEndOfPipeline(final TransportChannel underlayChannel, final ChannelPipeline pipeline) {
        if (authProvider != null) {
            pipeline.addLast(authProvider);
        }
        pipeline.addLast(dispatcher);

        // signal client transport is ready to send requests
        // NB. while server signals readiness on exit from initChannel(),
        // client needs additional confirmation for upgrade completion in case of HTTP/2 cleartext flow
        addTransportChannel(new HTTPTransportChannel(underlayChannel, scheme()));
    }

    abstract void initializePipeline(TransportChannel underlayChannel, ChannelPipeline pipeline,
            Http2ConnectionHandler connectionHandler);
}
