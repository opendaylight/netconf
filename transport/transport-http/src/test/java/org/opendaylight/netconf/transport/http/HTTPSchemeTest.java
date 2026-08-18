/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HTTPSchemeTest {
    /**
     * The {@link Http1RequestDispatcher} decides whether a request is aggregated, hence it has to sit in front of
     * {@link io.netty.handler.codec.http.HttpObjectAggregator}. Note the handlers are installed via repeated
     * {@code addAfter()} against the same base name, which inserts in reverse. That is easy to get backwards, and
     * nothing else in the suite notices, since an aggregated request behaves identically either way.
     */
    @Test
    void cleartextHttp1PipelineHasDispatcherBeforeAggregator() {
        final var channel = new EmbeddedChannel();
        channel.pipeline().addLast("anchor", new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(final ChannelHandlerContext ctx) {
                HTTPScheme.HTTP.initializeServerPipeline(ctx, HTTPServerLimits.DEFAULT);
            }
        });

        // a plain HTTP/1.1 request makes CleartextUpgradeHandler configure the HTTP/1.1 flow
        channel.writeInbound(Unpooled.copiedBuffer("GET /restconf/data HTTP/1.1\r\nHost: localhost\r\n\r\n",
            CharsetUtil.UTF_8));

        final var names = channel.pipeline().names();
        final var dispatcher = names.indexOf(Http1RequestDispatcher.HANDLER_NAME);
        final var aggregator = names.indexOf(Http1RequestDispatcher.AGGREGATOR_NAME);
        assertTrue(dispatcher >= 0, () -> "no dispatcher in " + names);
        assertTrue(aggregator >= 0, () -> "no aggregator in " + names);
        assertTrue(dispatcher < aggregator, () -> "dispatcher must precede aggregator in " + names);

        channel.finishAndReleaseAll();
    }

    @ParameterizedTest
    @MethodSource
    void hostUriOfValid(final String expected, final HTTPScheme scheme, final String host) throws Exception {
        assertEquals(URI.create(expected), scheme.hostUriOf(host));
    }

    private static List<Arguments> hostUriOfValid() {
        return List.of(
            Arguments.of("http://foo", HTTPScheme.HTTP, "foo"),
            Arguments.of("https://bar:1234", HTTPScheme.HTTPS, "bar:1234"));
    }

    @Test
    void hostUriOfInvalidPort() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("foo:abc"));
        assertEquals("Illegal character in port number at index 11: http://foo:abc", ex.getMessage());
    }

    @Test
    void hostUriOfInvalidHostname() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("--"));
        assertEquals("Illegal character in hostname at index 7: http://--", ex.getMessage());
    }

    @Test
    void hostUriOfWithUser() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("user@host"));
        assertEquals("Host contains userinfo: user@host", ex.getMessage());
    }
}
