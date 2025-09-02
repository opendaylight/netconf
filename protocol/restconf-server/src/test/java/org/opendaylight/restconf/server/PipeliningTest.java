/*
 * Copyright (c) 2025 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.server.TestUtils.ERROR_TAG_MAPPING;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;


class PipeliningTest extends AbstractRequestProcessorTest {

    @Mock
    private RestconfServer server;
    @Mock
    private PrincipalService principalService;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private RestconfStream.Registry streamRegistry;

    private RestconfSession session;

    @BeforeEach
    void beforeEach() {
        session = new RestconfSession(HTTPScheme.HTTP, new LocalAddress("test"),
            new EndpointRoot(principalService, WELL_KNOWN, Map.of(BASE_PATH.substring(1),
                new APIResource(new EndpointInvariants(server, PRETTY_PRINT, ERROR_TAG_MAPPING, MessageEncoding.JSON,
                    URI.create("/rests/")),
                    List.of(), 1000, Integer.MAX_VALUE, streamRegistry))));
        doReturn(channel).when(ctx).channel();
        doReturn(pipeline).when(ctx).pipeline();
        doReturn(pipeline).when(pipeline).addBefore(any(), isNull(), any());
        doReturn(new InetSocketAddress(0)).when(channel).remoteAddress();
        session.handlerAdded(ctx);
        when(ctx.executor()).thenReturn(ImmediateEventExecutor.INSTANCE);
    }

    @Test
    void testPipeliningQueue() {
        final var request1 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request2 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);
        final var request3 = buildRequest(HttpMethod.GET, DATA_PATH, TestUtils.TestEncoding.JSON, null);

        // Dispatch all requests manually
        session.channelRead0(ctx, request1);
        session.channelRead0(ctx, request2);
        session.channelRead0(ctx, request3);

        // At this point, all requests are queued
        assertEquals(3, blockedRequestsSize());

        // Manually finish each request to simulate pipeline processing
        session.notifyRequestFinished(ctx); // finishes request1
        assertEquals(2, blockedRequestsSize());
        session.notifyRequestFinished(ctx); // finishes request2
        assertEquals(1, blockedRequestsSize());
        session.notifyRequestFinished(ctx); // finishes request3
        assertEquals(0, blockedRequestsSize());
    }

    int blockedRequestsSize() {
        return session.blockedRequestsSize();
    }

}
