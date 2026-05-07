/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerPipelineSetup;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class RestconfSessionBootstrapTest {
    @Mock
    private PrincipalService principalService;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelHandlerContext codecCtx;
    @Mock
    private ChannelPipeline parentPipeline;
    @Mock
    private Channel parentChannel;
    @Mock
    private Channel childChannel;
    @Mock
    private ChannelConfig childChannelConfig;
    @Mock
    private ChannelPipeline childPipeline;

    /**
     * Verifies that the HTTP/2 child stream initializer reads {@code SETTINGS_MAX_FRAME_SIZE} from the
     * {@link Http2FrameCodec} encoder lazily — at stream-creation time — rather than at pipeline-setup time.
     *
     * <p>The test simulates the race that existed before the fix: {@code configureHttp2()} fires immediately
     * after the codec is installed into the pipeline (before any bytes are read from the wire), so at that
     * point {@code frameSizePolicy().maxFrameSize()} still holds the Netty default (16384). The peer's
     * {@code SETTINGS} frame arrives and is processed only afterwards. The {@link ConcurrentRestconfSession}
     * created for each stream must reflect the post-{@code SETTINGS} value, not the pre-{@code SETTINGS}
     * snapshot.
     */
    @Test
    @SuppressWarnings("unchecked")
    void http2ChildInitializerReadsFrameSizeFromCodecAtStreamCreationTime() throws Exception {
        // Simulate the codec reporting the Netty default initially, then the peer-advertised value after
        // SETTINGS exchange — exactly the ordering that occurs in a live connection.
        final var currentFrameSize = new AtomicInteger(16384);
        final var mockCodec = mock(Http2FrameCodec.class, RETURNS_DEEP_STUBS);
        when(mockCodec.encoder().configuration().frameSizePolicy().maxFrameSize())
            .thenAnswer(inv -> currentFrameSize.get());

        // Parent channel/pipeline setup
        doReturn(parentPipeline).when(ctx).pipeline();
        doReturn(null).when(parentPipeline).context("h2-multiplexer");
        doReturn(codecCtx).when(parentPipeline).context(Http2FrameCodec.class);
        doReturn("h2-frame-codec").when(codecCtx).name();
        doReturn(mockCodec).when(parentPipeline).get(Http2FrameCodec.class);
        doReturn(parentChannel).when(ctx).channel();
        doReturn(new InetSocketAddress(0)).when(parentChannel).remoteAddress();

        // Child channel setup
        doReturn(childChannelConfig).when(childChannel).config();
        doReturn(childPipeline).when(childChannel).pipeline();

        final var root = new EndpointRoot(principalService, new WellKnownResources("/restconf"), Map.of());
        final var bootstrap = new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(262144), Uint32.valueOf(16384),
            new WriteBufferWaterMark(32768, 65536), new AltSvcAdvertiser("h3=\":8443\"; ma=3600"));

        // Fire the HTTP/2 setup event — at this point the codec still reports 16384 (pre-SETTINGS).
        // With the old code this would have snapshot-captured 16384 into the child initializer.
        bootstrap.userEventTriggered(ctx, HTTPServerPipelineSetup.HTTP_2);

        // Simulate the peer SETTINGS frame being received and applied: SETTINGS_MAX_FRAME_SIZE = 65536.
        final int peerFrameSize = 65536;
        currentFrameSize.set(peerFrameSize);

        // Extract the ChannelInitializer from the Http2MultiplexHandler installed on the parent pipeline.
        final var mhCaptor = ArgumentCaptor.forClass(Http2MultiplexHandler.class);
        verify(parentPipeline).addAfter(eq("h2-frame-codec"), eq("h2-multiplexer"), mhCaptor.capture());
        final var inboundField = Http2MultiplexHandler.class.getDeclaredField("inboundStreamHandler");
        inboundField.setAccessible(true);
        final var initializer = (ChannelInitializer<Channel>) inboundField.get(mhCaptor.getValue());

        // Invoke initChannel() directly, mirroring what Http2MultiplexHandler does when the first
        // HEADERS frame arrives — after SETTINGS exchange is complete.
        final var initChannelMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
        initChannelMethod.setAccessible(true);
        initChannelMethod.invoke(initializer, childChannel);

        // The ConcurrentRestconfSession must carry the peer-advertised frame size (65536), not the
        // pre-SETTINGS default (16384) that was visible when configureHttp2() ran.
        final var sessionCaptor = ArgumentCaptor.forClass(ConcurrentRestconfSession.class);
        verify(childPipeline).addLast(eq("restconf-session"), sessionCaptor.capture());
        final var chunkSizeField = HTTPServerSession.class.getDeclaredField("chunkSize");
        chunkSizeField.setAccessible(true);
        assertEquals(Uint32.valueOf(peerFrameSize), chunkSizeField.get(sessionCaptor.getValue()),
            "HTTP/2 session chunk size must equal the peer-negotiated SETTINGS_MAX_FRAME_SIZE read "
                + "at stream-creation time, not the pre-SETTINGS default captured at pipeline-setup time");
    }
}
