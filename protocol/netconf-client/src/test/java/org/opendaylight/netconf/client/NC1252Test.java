/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.tls.tls.TcpClientParametersBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class NC1252Test {
    private static DefaultNetconfTimer TIMER;
    private static NetconfClientFactoryImpl FACTORY;

    @Mock
    private NetconfClientSessionListener sessionListener;

    @BeforeAll
    static void beforeAll() {
        TIMER = new DefaultNetconfTimer();
        FACTORY = new NetconfClientFactoryImpl(TIMER);
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
        TIMER.close();
    }

    @Test
    void tcpClientNoServer() throws Exception {
        // Find a free local port
        final int localPort;
        try (var socket = new ServerSocket(0)) {
            localPort = socket.getLocalPort();
        }

        final var clientConfig = NetconfClientConfigurationBuilder.create()
            .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
            .withTcpParameters(new TcpClientParametersBuilder()
                .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress())))
                .setRemotePort(new PortNumber(Uint16.valueOf(localPort)))
                .build())
            .withSessionListener(sessionListener)
            .build();
        final var future = FACTORY.createClient(clientConfig);

        final var ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(ConnectException.class, ex);
        assertThat(ex.getMessage()).startsWith("Connection refused: ");
    }
}
