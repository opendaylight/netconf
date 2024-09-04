/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
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
    private final ClientRequestDispatcher dispatcher;
    private final ClientAuthProvider authProvider;
    private final boolean http2;

    private HTTPClient(final TransportChannelListener listener, final ClientAuthProvider authProvider,
            final boolean http2) {
        super(listener);
        this.authProvider = authProvider;
        this.http2 = http2;
        dispatcher = http2 ? new ClientHttp2RequestDispatcher() : new ClientHttp1RequestDispatcher();
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

        final var client = new HTTPClient(listener, ClientAuthProvider.ofNullable(httpParams), http2);
        final var underlay = tlsParams == null
            ? TCPClient.connect(client.asListener(), bootstrap, tcpParams)
            : TLSClient.connect(client.asListener(), bootstrap, tcpParams, new HttpSslHandlerFactory(tlsParams, http2));
        return transformUnderlay(client, underlay);
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var pipeline = underlayChannel.channel().pipeline();
        final boolean ssl = pipeline.get(SslHandler.class) != null;

        if (http2) {
            // External HTTP 2 to internal HTTP 1.1 adapter handler
            final var connectionHandler = Http2Utils.connectionHandler(false, MAX_HTTP_CONTENT_LENGTH);
            if (ssl) {
                // Application protocol negotiator over TLS
                pipeline.addLast(apnHandler(underlayChannel, connectionHandler));
            } else {
                // Cleartext upgrade flow
                final var sourceCodec = new HttpClientCodec();
                final var upgradeHandler = new HttpClientUpgradeHandler(sourceCodec,
                    new Http2ClientUpgradeCodec(connectionHandler), MAX_HTTP_CONTENT_LENGTH);
                pipeline.addLast(sourceCodec, upgradeHandler, upgradeRequestHandler(underlayChannel));
            }

        } else {
            // HTTP 1.1
            pipeline.addLast(new HttpClientCodec(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
            configureEndOfPipeline(pipeline);
            addTransportChannel(new HTTPTransportChannel(underlayChannel));
        }
    }

    private void configureEndOfPipeline(final ChannelPipeline pipeline) {
        if (http2) {
            pipeline.addLast(Http2Utils.clientSettingsHandler());
        }
        if (authProvider != null) {
            pipeline.addLast(authProvider);
        }
        pipeline.addLast(dispatcher);

        // signal client transport is ready to send requests
        // NB. while server signals readiness on exit from initChannel(),
        // client needs additional confirmation for upgrade completion in case of HTTP/2 cleartext flow
    }

    private ApplicationProtocolNegotiationHandler apnHandler(final TransportChannel underlayChannel,
            final Http2ConnectionHandler connectionHandler) {
        return new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    final var pipeline = ctx.pipeline();
                    pipeline.addLast(connectionHandler);
                    configureEndOfPipeline(pipeline);
                    addTransportChannel(new HTTPTransportChannel(underlayChannel));
                    return;
                }
                ctx.close();
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        };
    }

    private ChannelHandler upgradeRequestHandler(final TransportChannel underlayChannel) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                // trigger upgrade by simple GET request;
                // required headers and flow will be handled by HttpClientUpgradeHandler
                ctx.writeAndFlush(new DefaultFullHttpRequest(HTTP_1_1, GET, "/", EMPTY_BUFFER));
                ctx.fireChannelActive();
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                // process upgrade result
                if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                    final var pipeline = ctx.pipeline();
                    configureEndOfPipeline(pipeline);
                    pipeline.remove(this);
                    addTransportChannel(new HTTPTransportChannel(underlayChannel));
                } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
                    notifyTransportChannelFailed(new IllegalStateException("Server rejected HTTP/2 upgrade request"));
                }
            }
        };
    }
}
