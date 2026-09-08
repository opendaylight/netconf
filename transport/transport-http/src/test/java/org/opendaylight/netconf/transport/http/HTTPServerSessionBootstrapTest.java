/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.common.Uint32;

class HTTPServerSessionBootstrapTest {
    @Test
    void validatesRequestLimitsAtConstruction() {
        for (int index = 0; index < 4; ++index) {
            for (var invalid : new Uint32[] { Uint32.ZERO, Uint32.valueOf(2147483648L), Uint32.MAX_VALUE }) {
                final var limits = new Uint32[] { Uint32.ONE, Uint32.ONE, Uint32.ONE, Uint32.ONE };
                limits[index] = invalid;
                assertThrows(IllegalArgumentException.class, () -> bootstrap(limits));
            }
            final var limits = new Uint32[] { Uint32.ONE, Uint32.ONE, Uint32.ONE, Uint32.ONE };
            limits[index] = null;
            assertThrows(NullPointerException.class, () -> bootstrap(limits));
        }
        assertDoesNotThrow(() -> bootstrap(new Uint32[] { Uint32.ONE, Uint32.ONE, Uint32.ONE, Uint32.ONE }));
        final var max = Uint32.valueOf(Integer.MAX_VALUE);
        assertDoesNotThrow(() -> bootstrap(new Uint32[] { max, max, max, max }));
    }

    @Test
    void defaultConstructorUsesSharedRequestLimits() {
        final var ctx = mock(ChannelHandlerContext.class);
        final var frameSize = Uint32.valueOf(32768);
        final var bootstrap = new HTTPServerSessionBootstrap(HTTPScheme.HTTPS, frameSize) {
            @Override
            protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext context) {
                throw new AssertionError("Unexpected pipeline setup");
            }

            @Override
            protected void configureHttp2(final ChannelHandlerContext context) {
                throw new AssertionError("Unexpected pipeline setup");
            }
        };
        doReturn(mock(ChannelPipeline.class)).when(ctx).pipeline();
        try (var constructed = mockConstruction(HTTPScheme.AlpnUpgradeHandler.class, (handler, context) ->
                assertEquals(List.of(frameSize, Uint32.valueOf(8192), Uint32.valueOf(16384), Uint32.valueOf(8192),
                    Uint32.valueOf(10485760)), context.arguments()))) {
            bootstrap.handlerAdded(ctx);
            assertEquals(1, constructed.constructed().size());
        }
    }

    private static HTTPServerSessionBootstrap bootstrap(final Uint32[] limits) {
        return new HTTPServerSessionBootstrap(HTTPScheme.HTTP, Uint32.valueOf(16384),
                limits[0], limits[1], limits[2], limits[3]) {
            @Override
            protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
                throw new AssertionError("Unexpected pipeline setup");
            }

            @Override
            protected void configureHttp2(final ChannelHandlerContext ctx) {
                throw new AssertionError("Unexpected pipeline setup");
            }
        };
    }
}
