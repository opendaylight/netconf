/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsAuthProvider;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

@ExtendWith(MockitoExtension.class)
public class CallHomeMountServiceTest {

    private static final SocketAddress SOCKET_ADDRESS = new InetSocketAddress("127.0.0.1", 12345);
    private static final String ID1 = "id1";
    private static final NodeId NODE_ID1 = new NodeId(ID1);
    private static final String ID2 = "id2";

    @Mock
    private CallHomeTopology topology;
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private ClientSession sshSession;
    @Mock
    private Channel nettyChannel;
    @Mock
    private CallHomeTlsAuthProvider tlsAuthProvider;
    @Mock
    private CallHomeStatusRecorder statusRecorder;

    private CallHomeMountService service;
    private ListenableFuture<NetconfClientSession> netconfSessionFuture;

    @BeforeEach
    void beforeEach() {
        service = new CallHomeMountService(topology, defaultConfig());
        /*
         * Reproduce behavior of org.opendaylight.netconf.topology.spi.AbstractNetconfTopology#ensureNode(Node)
         * for ID1 only.
         */
        doAnswer(invocation -> {
            final var node = (Node) invocation.getArguments()[0];
            if (ID1.equals(node.requireNodeId().getValue())) {
                final var configBuilderFactory = service.createClientConfigurationBuilderFactory();
                final var config = configBuilderFactory
                    .createClientConfigurationBuilder(node.requireNodeId(), node.augmentation(NetconfNode.class))
                    .withSessionListener(sessionListener).build();
                try {
                    netconfSessionFuture = service.createClientFactory().createClient(config);
                } catch (UnsupportedConfigurationException e) {
                    netconfSessionFuture = null;
                }
            } else {
                netconfSessionFuture = null;
            }
            return null;
        }).when(topology).enableNode(any(Node.class));
        doNothing().when(topology).disableNode(any(NodeId.class));
    }

    @Test
    void sshSessionContextManager() throws Exception {
        doReturn(SOCKET_ADDRESS).when(sshSession).getRemoteAddress();
        final var sshSessionContextManager = service.createSshSessionContextManager();

        // id 1 -- netconf layer created
        final var context = sshSessionContextManager.createContext(ID1, sshSession);
        assertNotNull(context);
        assertEquals(ID1, context.id());
        assertEquals(SOCKET_ADDRESS, context.remoteAddress());
        assertSame(sshSession, context.sshSession());
        assertSame(sessionListener, context.netconfSessionListener());
        assertNotNull(context.settableFuture());
        assertSame(netconfSessionFuture, context.settableFuture());
        // id 2 -- netconf layer omitted
        assertNull(sshSessionContextManager.createContext(ID2, sshSession));

        // remove context
        sshSessionContextManager.remove(ID1);
        verify(topology, times(1)).disableNode(eq(NODE_ID1));
    }

    @Test
    void tlsSessionContextManager() {
        doReturn(SOCKET_ADDRESS).when(nettyChannel).remoteAddress();
        final var tlsSessionContextManager = service.createTlsSessionContextManager(tlsAuthProvider, statusRecorder);

        // id 1 -- netconf layer created
        final var context = tlsSessionContextManager.createContext(ID1, nettyChannel);
        assertNotNull(context);
        assertEquals(ID1, context.id());
        assertSame(nettyChannel, context.nettyChannel());
        assertSame(sessionListener, context.netconfSessionListener());
        assertNotNull(context.settableFuture());
        assertSame(netconfSessionFuture, context.settableFuture());
        // id 2 -- netconf layer omitted
        assertNull(tlsSessionContextManager.createContext(ID2, nettyChannel));

        // remove context
        tlsSessionContextManager.remove(ID1);
        verify(topology, times(1)).disableNode(eq(NODE_ID1));
    }

    private static CallHomeMountService.Configuration defaultConfig() {
        return new CallHomeMountService.Configuration() {
            @Override
            public String host() {
                return "0.0.0.0";
            }

            @Override
            public int sshPort() {
                return 4334;
            }

            @Override
            public int tlsPort() {
                return 4335;
            }

            @Override
            public int connectionTimeoutMillis() {
                return 10_000;
            }

            @Override
            public int maxConnections() {
                return 64;
            }

            @Override
            public int keepAliveDelay() {
                return 120;
            }

            @Override
            public int requestTimeoutMillis() {
                return 60000;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }
        };
    }
}
