/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.Http2Utils.copyStreamId;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannel;
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
    static final String REQUEST_DISPATCHER_HANDLER_NAME = "request-dispatcher";

    private final AuthHandlerFactory authHandlerFactory;
    private final @NonNull RequestDispatcher dispatcher;

    private HTTPServer(final TransportChannelListener listener, final RequestDispatcher dispatcher,
            final AuthHandlerFactory authHandlerFactory) {
        super(listener);
        this.dispatcher = requireNonNull(dispatcher);
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

        final var server = new HTTPServer(listener, dispatcher,
            authHandlerFactory != null ? authHandlerFactory : BasicAuthHandlerFactory.ofNullable(httpParams));
        final var underlay = tlsParams == null
            ? TCPServer.listen(server.asListener(), bootstrap, tcpParams)
            : TLSServer.listen(server.asListener(), bootstrap, tcpParams, new HttpSslHandlerFactory(tlsParams));
        return transformUnderlay(server, underlay);
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var pipeline = underlayChannel.channel().pipeline();
        final var ssl = pipeline.get(SslHandler.class) != null;

        // External HTTP 2 to internal HTTP 1.1 adapter handler
        final var connectionHandler = Http2Utils.connectionHandler(true, MAX_HTTP_CONTENT_LENGTH);
        if (ssl) {
            // Application protocol negotiator over TLS
            pipeline.addLast(apnHandler(connectionHandler));
        } else {
            // Cleartext upgrade flow
            final var sourceCodec = new HttpServerCodec();
            final var upgradeHandler =
                new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(connectionHandler));
            pipeline.addLast(new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, connectionHandler),
                upgradeResultHandler());
        }

        addTransportChannel(new HTTPTransportChannel(underlayChannel));
    }

    private ApplicationProtocolNegotiationHandler apnHandler(final Http2ConnectionHandler connectionHandler) {
        return new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                final var pipeline = ctx.pipeline();

                switch (protocol) {
                    case null -> throw new NullPointerException();
                    case ApplicationProtocolNames.HTTP_1_1 -> {
                        pipeline.addLast(new HttpServerCodec(),
                            new HttpServerKeepAliveHandler(),
                            new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
                    }
                    case ApplicationProtocolNames.HTTP_2 -> {
                        pipeline.addLast(connectionHandler);
                    }
                    default -> throw new IllegalStateException("unknown protocol: " + protocol);
                }

                configureEndOfPipeline(pipeline);
            }
        };
    }

    private void configureEndOfPipeline(final ChannelPipeline pipeline) {
        if (authHandlerFactory != null) {
            pipeline.addLast(authHandlerFactory.create());
        }
        pipeline.addLast(REQUEST_DISPATCHER_HANDLER_NAME, serverHandler(dispatcher));
    }

    private ChannelHandler upgradeResultHandler() {
        // the handler processes cleartext upgrade result

        return new SimpleChannelInboundHandler<HttpMessage>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage request) throws Exception {
                // if there was no upgrade to HTTP/2 the incoming message is accepted via channel read;
                // configure HTTP 1.1 flow, pass the message further the pipeline, remove self as no longer required
                final var pipeline = ctx.pipeline();
                pipeline.addLast(new HttpServerKeepAliveHandler(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
                configureEndOfPipeline(pipeline);
                ctx.fireChannelRead(ReferenceCountUtil.retain(request));
                pipeline.remove(this);
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
                // if there was upgrade to HTTP/2 the upgrade event is fired further the pipeline;
                // on event occurrence it's only required to complete the configuration for future requests,
                // then remove self as no longer required
                if (event instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                    final var pipeline = ctx.pipeline();
                    configureEndOfPipeline(pipeline);
                    pipeline.remove(this);
                }
            }
        };
    }

    private static HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory(
            final Http2ConnectionHandler connectionHandler) {
        return protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
            ? new Http2ServerUpgradeCodec(connectionHandler) : null;
    }

    private static ChannelHandler serverHandler(final RequestDispatcher dispatcher) {
        return new SimpleChannelInboundHandler<FullHttpRequest>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
                dispatcher.dispatch(request.retain(), new FutureCallback<>() {
                    @Override
                    public void onSuccess(final FullHttpResponse response) {
                        copyStreamId(request, response);
                        request.release();
                        ctx.writeAndFlush(response);
                    }

                    @Override
                    public void onFailure(final Throwable throwable) {
                        final var message = throwable.getMessage();
                        final var content = message == null ? EMPTY_BUFFER
                            : Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
                        final var response = new DefaultFullHttpResponse(request.protocolVersion(),
                            INTERNAL_SERVER_ERROR, content);
                        response.headers()
                            .set(CONTENT_TYPE, TEXT_PLAIN)
                            .setInt(CONTENT_LENGTH, response.content().readableBytes());
                        onSuccess(response);
                    }
                });
            }
        };
    }
}
