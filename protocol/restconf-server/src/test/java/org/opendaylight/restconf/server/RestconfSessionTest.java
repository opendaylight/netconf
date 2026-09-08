/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap.DEFAULT_MAX_REQUEST_BODY_SIZE;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerPipelineSetup;
import org.opendaylight.netconf.transport.http.HTTPTransportChannel;
import org.opendaylight.netconf.transport.http.HttpRequestLimits;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class RestconfSessionTest {
    @Mock
    private RestconfServer server;
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Mock
    private PrincipalService principalService;
    @Mock
    private HttpServerStackGrouping httpServerStackGrouping;
    @Mock
    private HTTPTransportChannel transportChannel;
    @Mock
    private Channel channel;
    @Mock
    private ChannelConfig channelConfig;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private Registration registration;
    @Mock
    private ChannelHandlerContext ctx;
    @Captor
    private ArgumentCaptor<RestconfSessionBootstrap> bootstrapCaptor;
    @Captor
    private ArgumentCaptor<RestconfSession> sessionCaptor;

    @ParameterizedTest(name = "HTTP {1} before authentication")
    @MethodSource
    void decoderFailuresAreRejectedBeforeAuthentication(final String request, final int status) {
        final var authHandler = spy(newAuthHandler());
        final var embedded = newHttp1Channel(authHandler);
        try {
            embedded.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII));
            embedded.runPendingTasks();

            final var response = responseText(embedded);
            assertTrue(response.startsWith("HTTP/1.1 " + status + " "), response);
            assertFalse(embedded.isActive());
            verify(authHandler, never()).authenticate(any(HttpRequest.class));
            verify(principalService, never()).acquirePrincipal(any());
        } finally {
            embedded.finishAndReleaseAll();
        }
    }

    private static List<Arguments> decoderFailuresAreRejectedBeforeAuthentication() {
        return List.of(
            Arguments.of("GET /" + "a".repeat(128) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", 414),
            Arguments.of("GET /restconf HTTP/1.1\r\nHost: localhost\r\nX-Padding: "
                + "a".repeat(256) + "\r\n\r\n", 431));
    }

    @Test
    void validRequestWithoutCredentialsIsRejectedWith401() {
        final var authHandler = spy(newAuthHandler());
        final var embedded = newHttp1Channel(authHandler);
        try {
            embedded.writeInbound(Unpooled.copiedBuffer(
                "GET /restconf HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
            embedded.runPendingTasks();

            final var response = responseText(embedded);
            assertTrue(response.startsWith("HTTP/1.1 401 "), response);
            verify(authHandler).authenticate(any(HttpRequest.class));
            verify(principalService, never()).acquirePrincipal(any());
        } finally {
            embedded.finishAndReleaseAll();
        }
    }

    private EmbeddedChannel newHttp1Channel(final AbstractBasicAuthHandler<String> authHandler) {
        doReturn(authHandler).when(principalService).create();
        final var root = new EndpointRoot(principalService, new WellKnownResources("/restconf"), Map.of());
        return new EmbeddedChannel(new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(8192), Uint32.valueOf(16384),
            new HttpRequestLimits(Uint32.valueOf(64), Uint32.valueOf(128), Uint32.valueOf(8192),
                Uint32.valueOf(DEFAULT_MAX_REQUEST_BODY_SIZE)),
            new WriteBufferWaterMark(32768, 65536)));
    }

    private static AbstractBasicAuthHandler<String> newAuthHandler() {
        return new AbstractBasicAuthHandler<>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return null;
            }
        };
    }

    private static String responseText(final EmbeddedChannel embedded) {
        final var response = assertInstanceOf(ByteBuf.class, embedded.readOutbound());
        try {
            return response.toString(CharsetUtil.US_ASCII);
        } finally {
            response.release();
        }
    }

    @Test
    void closeRestconfSessionResourcesTest() throws Exception {
        // setup
        doReturn(new InetSocketAddress(0)).when(channel).remoteAddress();
        doReturn(channel).when(transportChannel).channel();
        doReturn(HTTPScheme.HTTP).when(transportChannel).scheme();
        doReturn(channel).when(ctx).channel();
        doReturn(channelConfig).when(channel).config();
        doReturn(pipeline).when(ctx).pipeline();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(pipeline).when(pipeline).addBefore(any(), isNull(), any());
        doReturn(pipeline).when(pipeline).addLast(any());

        // default config just for testing purposes
        final var configuration = new NettyEndpointConfiguration(ErrorTagMapping.RFC8040, PrettyPrintParam.TRUE,
            Uint16.ZERO, Uint32.valueOf(10_000), "restconf", MessageEncoding.JSON, httpServerStackGrouping,
            Uint32.valueOf(256 * 1024), Uint32.valueOf(16 * 1024), Uint32.valueOf(32 * 1024),
            Uint32.valueOf(64 * 1024));

        final var listener = new RestconfTransportChannelListener(server, streamRegistry, principalService,
            configuration, new WriteBufferWaterMark(32 * 1024, 64 * 1024));
        listener.onTransportChannelEstablished(transportChannel);

        // capture created bootstrap
        verify(pipeline).addLast(bootstrapCaptor.capture());
        final var bootstrap = bootstrapCaptor.getValue();
        bootstrap.userEventTriggered(ctx, HTTPServerPipelineSetup.HTTP_11);

        // capture created session
        verify(pipeline).replace(eq(bootstrap), isNull(), sessionCaptor.capture());
        final var session = sessionCaptor.getValue();
        session.handlerAdded(ctx);

        // register resource
        session.transportSession().registerResource(registration);
        // bring the channel down
        session.channelInactive(ctx);
        // verify resource was closed on channel close
        verify(registration).close();
    }
}
