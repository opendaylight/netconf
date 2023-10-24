/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev230417.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;

abstract class AbstractNetconfServerTest {
    @Mock
    NetconfServerSessionNegotiatorFactory negotiatorFactory;
    @Mock
    TransportChannelListener clientListener;

    SSHTransportStackFactory bootstrapFactory;
    TcpClientGrouping tcpClientParams;
    TcpServerGrouping tcpServerParams;

    ServerTransportInitializer initializer;

    @BeforeEach
    final void beforeEach() throws Exception {
        bootstrapFactory = new SSHTransportStackFactory("test", 0);

        // create temp socket to get available port for test
        final IpAddress address;
        final PortNumber port;
        try (var socket = new ServerSocket(0)) {
            address = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());
            port = new PortNumber(Uint16.valueOf(socket.getLocalPort()));
        }

        initializer = new ServerTransportInitializer(negotiatorFactory);
        tcpServerParams = new TcpServerParametersBuilder().setLocalAddress(address).setLocalPort(port).build();
        tcpClientParams = new TcpClientParametersBuilder()
            .setRemoteAddress(new Host(address))
            .setRemotePort(port)
            .build();
    }

    @AfterEach
    final void afterEach() {
        bootstrapFactory.close();
    }
}
