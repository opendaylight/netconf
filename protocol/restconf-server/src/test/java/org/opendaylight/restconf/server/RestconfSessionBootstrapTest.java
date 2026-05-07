/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockConstruction;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class RestconfSessionBootstrapTest {
    private static final int CHUNK_SIZE_ARG_INDEX = 3;

    @Mock
    private PrincipalService principalService;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPipeline parentPipeline;
    @Mock
    private Channel parentChannel;

    /**
     * Verifies that the HTTP/2 child stream initializer reads {@code SETTINGS_MAX_FRAME_SIZE} from the
     * {@link Http2FrameCodec} encoder lazily — at stream-creation time — rather than at pipeline-setup time.
     *
     * <p>The test simulates the timing window: the codec starts at the Netty default (16384) when the
     * initializer is built (mirroring {@code configureHttp2()} firing before any wire bytes), then advances
     * to the peer-advertised value before the child stream's {@code initChannel()} runs. The
     * {@link ConcurrentRestconfSession} created for the stream must be constructed with the post-{@code
     * SETTINGS} value.
     */
    @Test
    void http2ChildInitializerReadsFrameSizeFromCodecAtStreamCreationTime() throws Exception {
        // Real codec — its frame-size policy is a normal mutable config object whose maxFrameSize(int)
        // setter is the same one Netty calls when applying a peer SETTINGS_MAX_FRAME_SIZE update.
        final var codec = Http2FrameCodecBuilder.forServer().build();
        final var frameSizePolicy = codec.encoder().configuration().frameSizePolicy();

        doReturn(parentPipeline).when(ctx).pipeline();
        doReturn(codec).when(parentPipeline).get(Http2FrameCodec.class);
        doReturn(parentChannel).when(ctx).channel();
        doReturn(new InetSocketAddress(0)).when(parentChannel).remoteAddress();

        final var root = new EndpointRoot(principalService, new WellKnownResources("/restconf"), Map.of());
        final var bootstrap = new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(262144), Uint32.valueOf(16384),
            new WriteBufferWaterMark(32768, 65536), new AltSvcAdvertiser("h3=\":8443\"; ma=3600"));

        // Build the initializer while the codec still reports the pre-SETTINGS default (16384).
        final var initializer = bootstrap.buildHttp2ChildInitializer(ctx);

        // Peer SETTINGS arrives before the first stream is created.
        final var peerFrameSize = Uint32.valueOf(65536);
        frameSizePolicy.maxFrameSize(peerFrameSize.intValue());

        // Capture ConcurrentRestconfSession constructor arguments without touching the class.
        // The mocked session's lifecycle callbacks are Mockito no-ops, so no real handlerAdded /
        // ServerRequestExecutor / authHandlerFactory side effects fire during the EmbeddedChannel run.
        final var capturedArgs = new ArrayList<List<?>>();
        try (var mocked = mockConstruction(ConcurrentRestconfSession.class,
                (mock, mockCtx) -> capturedArgs.add(mockCtx.arguments()))) {
            new EmbeddedChannel(initializer);
        }

        assertEquals(1, capturedArgs.size(), "expected exactly one ConcurrentRestconfSession to be constructed");
        assertEquals(peerFrameSize, capturedArgs.get(0).get(CHUNK_SIZE_ARG_INDEX),
            "HTTP/2 session chunk size must equal the peer-negotiated SETTINGS_MAX_FRAME_SIZE read "
                + "at stream-creation time, not the pre-SETTINGS default captured at pipeline-setup time");
    }
}
