/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPTransportChannel;
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
    private ChannelPipeline pipeline;
    @Mock
    private ChannelFuture future;
    @Mock
    private Registration registration;
    @Mock
    private ChannelHandlerContext ctx;
    @Captor
    private ArgumentCaptor<RestconfSession> sessionCaptor;

    @Test
    void closeRestconfSessionResourcesTest() throws Exception {
        // setup
        doReturn(channel).when(transportChannel).channel();
        doReturn(channel).when(ctx).channel();
        doReturn(new InetSocketAddress(0)).when(channel).remoteAddress();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(pipeline).when(pipeline).addLast(any(ChannelHandler.class));
        doReturn(HTTPScheme.HTTP).when(transportChannel).scheme();
        // default config just for testing purposes
        final var configuration = new NettyEndpointConfiguration(ErrorTagMapping.RFC8040, PrettyPrintParam.TRUE,
            Uint16.ZERO, Uint32.valueOf(10_000), "restconf",
            MessageEncoding.JSON, httpServerStackGrouping);

        final var listener = new RestconfTransportChannelListener(server, streamRegistry, principalService,
            configuration);
        listener.onTransportChannelEstablished(transportChannel);
        // capture created session
        verify(pipeline).addLast(sessionCaptor.capture());
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
