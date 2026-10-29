/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerLimits;
import org.opendaylight.netconf.transport.http.PendingRequest;
import org.opendaylight.yangtools.yang.common.Uint32;

class RestconfStreamingLifecycleTest {
    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void disconnectOrRemovalReleasesCompletedQueuedRequest(final boolean chunked, final boolean removeHandler)
            throws Exception {
        final var root = mock(EndpointRoot.class);
        final var observed = new ArrayList<HttpRequest>();
        final AuthHandlerFactory auth = () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return "user";
            }

            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {
                observed.add(request);
                ctx.fireChannelRead(io.netty.util.ReferenceCountUtil.retain(request));
            }
        };
        doReturn(auth).when(root).authHandlerFactory();
        final var pending = mock(PendingRequest.class);
        doReturn(pending).when(root).prepare(any(), any(), any(), any());
        final var started = new CompletableFuture<Boolean>();
        doAnswer(invocation -> {
            started.complete(true);
            return null;
        }).when(pending).execute(any(), any());

        final var channel = new EmbeddedChannel(new RestconfSessionBootstrap(HTTPScheme.HTTP, root,
            Uint32.valueOf(1024), HTTPServerLimits.DEFAULT, WriteBufferWaterMark.DEFAULT,
            new AltSvcAdvertiser("")));
        try {
            channel.writeInbound(Unpooled.copiedBuffer(
                "POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                    + "transfer-encoding: chunked\r\n\r\n0\r\n\r\n", CharsetUtil.US_ASCII));
            started.get(5, TimeUnit.SECONDS);
            final var queuedBytes = Unpooled.copiedBuffer(
                "POST /restconf/data HTTP/1.1\r\nhost: localhost\r\n"
                    + (chunked ? "transfer-encoding: chunked\r\n\r\n4\r\nbody\r\n0\r\n\r\n"
                        : "content-length: 4\r\n\r\nbody"), CharsetUtil.US_ASCII);
            channel.writeInbound(queuedBytes);
            assertEquals(2, observed.size());
            final var queued = assertInstanceOf(ReferenceCounted.class, observed.get(1));
            assertEquals(1, queued.refCnt());
            final var session = channel.pipeline().get(RestconfSession.class);
            if (removeHandler) {
                channel.pipeline().remove(session);
            } else {
                channel.close();
            }
            assertEquals(0, queued.refCnt());
            assertEquals(0, queuedBytes.refCnt());
            assertEquals(0, session.blockedRequestsSize());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
