/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
public class TCPClientServerTest {
    @Mock
    private TcpClientGrouping clientGrouping;
    @Mock
    private TransportChannelListener clientListener;
    @Mock
    private TcpServerGrouping serverGrouping;
    @Mock
    private TransportChannelListener serverListener;

    private static EventLoopGroup group;

    @BeforeAll
    public static void beforeAll() {
        group = NettyTransportSupport.newEventLoopGroup("IntegrationTest");
    }

    @AfterAll
    public static void afterAll() {
        group.shutdownGracefully();
        group = null;
    }

    @Test
    public void integrationTest() throws Exception {
        // localhost address, so we do not leak things around
        final var loopbackAddr = IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress());

        // Server-side config
        doReturn(loopbackAddr).when(serverGrouping).getLocalAddress();
        doCallRealMethod().when(serverGrouping).requireLocalAddress();
        // note: this lets the server pick any port, we do not care
        doReturn(new PortNumber(Uint16.ZERO)).when(serverGrouping).getLocalPort();
        doCallRealMethod().when(serverGrouping).requireLocalPort();

        // Spin up the server and acquire its port
        final var server = TCPServer.listen(serverListener, NettyTransportSupport.newServerBootstrap().group(group),
            serverGrouping).get(2, TimeUnit.SECONDS);
        try {
            assertEquals("TCPServer{listener=serverListener}", server.toString());

            final var serverChannel = server.listenChannel();
            assertInstanceOf(ServerSocketChannel.class, serverChannel);
            final var serverPort = new PortNumber(
                Uint16.valueOf(((ServerSocketChannel) serverChannel).localAddress().getPort()));

            // Client-side config
            doReturn(new Host(loopbackAddr)).when(clientGrouping).getRemoteAddress();
            doCallRealMethod().when(clientGrouping).requireRemoteAddress();
            doReturn(serverPort).when(clientGrouping).getRemotePort();
            doCallRealMethod().when(clientGrouping).requireRemotePort();

            // Capture client and server channels
            final var serverCaptor = ArgumentCaptor.forClass(TransportChannel.class);
            doNothing().when(serverListener).onTransportChannelEstablished(serverCaptor.capture());
            final var clientCaptor = ArgumentCaptor.forClass(TransportChannel.class);
            doNothing().when(clientListener).onTransportChannelEstablished(clientCaptor.capture());

            final var client = TCPClient.connect(clientListener, NettyTransportSupport.newBootstrap().group(group),
                clientGrouping).get(2, TimeUnit.SECONDS);
            try {
                verify(serverListener, timeout(500)).onTransportChannelEstablished(any());
                final var serverTransports = serverCaptor.getAllValues();
                assertEquals(1, serverTransports.size());
                assertThat(serverTransports.get(0).toString(), allOf(
                    startsWith("TCPTransportChannel{channel=[id: "),
                    containsString(":" + serverPort.getValue() + " - R:")));

                verify(clientListener, timeout(500)).onTransportChannelEstablished(any());
                final var clientTransports = clientCaptor.getAllValues();
                assertEquals(1, clientTransports.size());
                assertThat(clientTransports.get(0).toString(), allOf(
                    startsWith("TCPTransportChannel{channel=[id: "),
                    endsWith(":" + serverPort.getValue() + "]}")));

                assertThat(client.toString(), allOf(
                    startsWith("TCPClient{listener=clientListener, state=TCPTransportChannel{channel=[id: 0x"),
                    endsWith(":" + serverPort.getValue() + "]}}")));
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }
}
