/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import java.net.InetAddress;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NettyTransportSupportTest {
    @ParameterizedTest
    @MethodSource("bootstrapFactoryMethods")
    void newBootstrapSetsChannelFactory(final Supplier<AbstractBootstrap<?, ?>> method) {
        final var bootstrap = method.get();
        assertNotNull(bootstrap);
        assertNotNull(bootstrap.config().channelFactory());
    }

    @ParameterizedTest
    @MethodSource("bootstrapFactoryMethods")
    void setTcpMd5WorksWithEpoll(final Supplier<AbstractBootstrap<?, ?>> method) throws Exception {
        assumeTrue(Epoll.isAvailable());

        final var bootstrap = method.get();
        final var secrets = TcpMd5Secrets.of(InetAddress.getLoopbackAddress(), "");
        NettyTransportSupport.setTcpMd5(bootstrap, secrets);
        final var option = bootstrap.config().options().get(EpollChannelOption.TCP_MD5SIG);
        assertNotNull(option);
        assertSame(secrets.map(), option);
    }

    @Test
    void getTcpKeepaliveOptionsWithEpoll() throws Exception {
        assumeTrue(Epoll.isAvailable());

        final var options = NettyTransportSupport.getTcpKeepaliveOptions();
        assertNotNull(options);
    }

    @Test
    void newEventLoopGroupWorks() {
        try (var group = NettyTransportSupport.newEventLoopGroup(42, Thread.ofVirtual().name("test-", 0).factory())) {
            assertNotNull(group);
        }
    }

    private static List<Supplier<AbstractBootstrap<?, ?>>> bootstrapFactoryMethods() {
        return List.of(
            NettyTransportSupport::newBootstrap,
            NettyTransportSupport::newDatagramBootstrap,
            NettyTransportSupport::newServerBootstrap);
    }
}
