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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
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
     * Both cleartext and ALPN upgrades install the HTTP/1 handlers relative to the upgrade handler. In particular, the
     * ALPN path repeatedly uses {@code addAfter()} against the same base, which inserts handlers in reverse order.
     */
    @ParameterizedTest
    @MethodSource
    void http1PipelineHasExpectedOrder(final HTTPScheme scheme) {
        final var anchor = new ChannelInboundHandlerAdapter();
        final var channel = new EmbeddedChannel(anchor);
        try {
            if (scheme == HTTPScheme.HTTPS) {
                final var sslHandler = mock(SslHandler.class);
                when(sslHandler.applicationProtocol()).thenReturn(ApplicationProtocolNames.HTTP_1_1);
                channel.pipeline().addLast(sslHandler);
            }
            scheme.initializeServerPipeline(channel.pipeline().context(anchor), HTTPServerLimits.DEFAULT);

            if (scheme == HTTPScheme.HTTP) {
                channel.writeInbound(Unpooled.copiedBuffer(
                    "GET /restconf/data HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
            } else {
                channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
            }

            final var expected = List.of(HttpServerCodec.class, HttpServerKeepAliveHandler.class,
                Http1RequestDispatcher.class, HttpObjectAggregator.class);
            final var actual = channel.pipeline().toMap().values().stream()
                .map(Object::getClass)
                .filter(expected::contains)
                .toList();
            assertEquals(expected, actual);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static List<HTTPScheme> http1PipelineHasExpectedOrder() {
        return List.of(HTTPScheme.HTTP, HTTPScheme.HTTPS);
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
