/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.TestUtils.ERROR_TAG_MAPPING;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.RestconfStream;

@ExtendWith(MockitoExtension.class)
class AbstractRequestProcessorTest {
    private static final PrettyPrintParam PRETTY_PRINT = PrettyPrintParam.FALSE;

    protected static final String BASE_PATH = "/rests";
    protected static final String HOST = "somehost:1234";
    protected static final URI BASE_URI = URI.create("http://" + HOST + BASE_PATH);
    protected static final String DATA_PATH = BASE_PATH + "/data";
    protected static final String ID_PATH = "test-model:root";
    protected static final String NEW_ID_PATH = "test-model:new";
    protected static final String MOUNT_PATH = "test-model:root/sub/tree/mount:point";
    protected static final TestEncoding DEFAULT_ENCODING = TestEncoding.JSON;
    protected static final String XML_CONTENT = "xml-content";
    protected static final String JSON_CONTENT = "json-content";

    protected static final ApiPath API_PATH;
    protected static final ApiPath NEW_API_PATH;
    protected static final ApiPath MOUNT_API_PATH;

    static {
        try {
            API_PATH = ApiPath.parse(ID_PATH);
            NEW_API_PATH = ApiPath.parse(NEW_ID_PATH);
            MOUNT_API_PATH = ApiPath.parse(MOUNT_PATH);
        } catch (ParseException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final WellKnownResources WELL_KNOWN = new WellKnownResources(BASE_PATH);

    @Mock
    protected RestconfServer server;
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
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

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
        doReturn(null).when(principalService).acquirePrincipal(any());
        doReturn(new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE).setSuccess())
            .when(ctx).writeAndFlush(any());
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    protected final FullHttpResponse dispatch(final FullHttpRequest request) {
        try {
            session.channelRead(ctx, request);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        verify(ctx, timeout(1000)).writeAndFlush(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    protected final FullHttpResponse dispatchWithAlloc(final FullHttpRequest request) {
        doReturn(UnpooledByteBufAllocator.DEFAULT).when(ctx).alloc();
        return dispatch(request);
    }

    protected static final List<Arguments> encodings() {
        return List.of(
            Arguments.of(TestEncoding.JSON, JSON_CONTENT),
            Arguments.of(TestEncoding.XML, XML_CONTENT)
        );
    }
}
