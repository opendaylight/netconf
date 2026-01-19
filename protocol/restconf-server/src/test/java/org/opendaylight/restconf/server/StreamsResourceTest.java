/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.TestUtils.ERROR_TAG_MAPPING;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class StreamsResourceTest {
    private static final String BASE_PATH = "/rests";
    private static final String STREAMS_PATH = BASE_PATH + "/streams/json/urn:uuid:f5c98414-5e3f-4e9a-b532-6231c52c777";
    private static final WellKnownResources WELL_KNOWN = new WellKnownResources(BASE_PATH);
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;
    private static final Uint32 CHUNK_SIZE = Uint32.valueOf(256 * 1024);

    @Mock
    private PrincipalService principalService;
    @Mock
    private RestconfServer server;
    @Mock
    private RestconfStream<?> stream;
    @Mock
    private Registration reg;
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private EventExecutor executor;
    @Mock
    private ChannelHandler contextHandler;
    @Captor
    private ArgumentCaptor<DefaultHttpResponse> streamResponseCaptor;

    private ConcurrentRestconfSession session;

    @BeforeEach
    void beforeEach() {
        session = new ConcurrentRestconfSession(HTTPScheme.HTTP, new LocalAddress("test"),
            new EndpointRoot(principalService, WELL_KNOWN, Map.of(BASE_PATH.substring(1),
                new APIResource(new EndpointInvariants(server, PRETTY_PRINT, ERROR_TAG_MAPPING, MessageEncoding.JSON,
                    URI.create("/rests/")),
                    List.of(), 1000, Integer.MAX_VALUE, streamRegistry))), CHUNK_SIZE);
        doReturn(channel).when(ctx).channel();
        doReturn(pipeline).when(ctx).pipeline();
        doReturn(pipeline).when(pipeline).addBefore(any(), isNull(), any());
        doReturn(new InetSocketAddress(0)).when(channel).remoteAddress();
        doReturn(null).when(principalService).acquirePrincipal(any());
        doReturn(new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE).setSuccess())
            .when(ctx).writeAndFlush(any());
        doReturn(stream).when(streamRegistry).lookupStream(any());
        doReturn(pipeline).when(pipeline).replace(any(ChannelHandler.class), anyString(), any(ChannelHandler.class));
        doReturn(pipeline).when(channel).pipeline();
        doReturn(ctx).when(pipeline).context(anyString());
        doReturn(executor).when(ctx).executor();
        doReturn(contextHandler).when(ctx).handler();
        session.handlerAdded(ctx);
    }

    @Test
    void testStreamResource() throws Exception {
        final var request = TestUtils.buildRequest(HttpMethod.GET, STREAMS_PATH, TestUtils.TestEncoding.JSON, null);
        request.headers().add(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM);

        doReturn(reg).when(stream).addSubscriber(any(), any(), any());
        assertResponse(dispatchStream(request), HttpResponseStatus.OK);
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    private DefaultHttpResponse dispatchStream(final FullHttpRequest request) throws Exception {
        session.channelRead(ctx, request);
        verify(ctx, timeout(1000)).writeAndFlush(streamResponseCaptor.capture());
        return streamResponseCaptor.getValue();
    }
}
