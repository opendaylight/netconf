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
import static org.mockito.Mockito.mockConstruction;
import static org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap.DEFAULT_MAX_HEADER_SIZE;
import static org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap.DEFAULT_MAX_REQUEST_BODY_SIZE;
import static org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap.DEFAULT_MAX_REQUEST_CHUNK_SIZE;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class HTTPServerSessionBootstrapTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPipeline pipeline;

    @ParameterizedTest(name = "limit[{0}] = {1}")
    @MethodSource
    void validatesRequestLimitsAtConstruction(final int index, final Uint32 value) {
        final var limits = new Uint32[] { Uint32.ONE, Uint32.ONE, Uint32.ONE, Uint32.ONE };
        limits[index] = value;
        final var expectedException = value == null ? NullPointerException.class : IllegalArgumentException.class;
        assertThrows(expectedException, () -> bootstrap(limits));
    }

    private static Stream<Arguments> validatesRequestLimitsAtConstruction() {
        return IntStream.range(0, 4).boxed().flatMap(index ->
            Stream.of(Uint32.ZERO, Uint32.valueOf(2147483648L), Uint32.MAX_VALUE, null)
                .map(value -> Arguments.of(index, value)));
    }

    @Test
    void acceptsRequestLimitBoundaries() {
        assertDoesNotThrow(() -> bootstrap(new Uint32[] { Uint32.ONE, Uint32.ONE, Uint32.ONE, Uint32.ONE }));
        final var max = Uint32.valueOf(Integer.MAX_VALUE);
        assertDoesNotThrow(() -> bootstrap(new Uint32[] { max, max, max, max }));
    }

    @Test
    void defaultConstructorUsesSharedRequestLimits() {
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
        doReturn(pipeline).when(ctx).pipeline();
        try (var constructed = mockConstruction(HTTPScheme.AlpnUpgradeHandler.class, (handler, context) ->
                assertEquals(List.of(frameSize, new HttpRequestLimits(Uint32.valueOf(DEFAULT_MAX_INITIAL_LINE_LENGTH),
                        Uint32.valueOf(DEFAULT_MAX_HEADER_SIZE), Uint32.valueOf(DEFAULT_MAX_REQUEST_CHUNK_SIZE),
                        Uint32.valueOf(DEFAULT_MAX_REQUEST_BODY_SIZE))),
                    context.arguments()))) {
            bootstrap.handlerAdded(ctx);
            assertEquals(1, constructed.constructed().size());
        }
    }

    private static HTTPServerSessionBootstrap bootstrap(final Uint32[] limits) {
        return new HTTPServerSessionBootstrap(HTTPScheme.HTTP, Uint32.valueOf(16384),
                new HttpRequestLimits(limits[0], limits[1], limits[2], limits[3])) {
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
