/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class CallHomeMountDispatcherTest {
    private String topologyId;
    private EventExecutor mockExecutor;
    private ScheduledThreadPool mockKeepAlive;
    private ThreadPool mockProcessingExecutor;
    private SchemaRepositoryProvider mockSchemaRepoProvider;

    private CallHomeMountDispatcher instance;
    private DataBroker mockDataBroker;
    private DOMMountPointService mockMount;

    private CallHomeMountSessionManager mockSessMgr;
    private CallHomeTopology mockTopology;
    private CallHomeProtocolSessionContext mockProtoSess;
    private AAAEncryptionService mockEncryptionService;
    private RpcProviderRegistry mockRpcProviderRegistry;
    
    @Before
    public void setup() {
        topologyId = "";
        mockExecutor = mock(EventExecutor.class);
        mockKeepAlive = mock(ScheduledThreadPool.class);
        mockProcessingExecutor = mock(ThreadPool.class);
        mockSchemaRepoProvider = mock(SchemaRepositoryProvider.class);
        mockDataBroker = mock(DataBroker.class);
        mockMount = mock(DOMMountPointService.class);
        mockSessMgr = mock(CallHomeMountSessionManager.class);
        mockTopology = mock(CallHomeTopology.class);
        mockProtoSess = mock(CallHomeProtocolSessionContext.class);
        mockEncryptionService = mock(AAAEncryptionService.class);
        mockRpcProviderRegistry = mock(RpcProviderRegistry.class);
        
        instance = new CallHomeMountDispatcher(topologyId, mockExecutor, mockKeepAlive,
                mockProcessingExecutor, mockSchemaRepoProvider, mockDataBroker, mockMount,
                mockEncryptionService, mockRpcProviderRegistry) {
            @Override
            public CallHomeMountSessionManager getSessionManager() {
                return mockSessMgr;
            }
            @Override
            void createTopology() {
                this.topology = mockTopology;
            }
        };
    }

    NetconfClientConfiguration someConfiguration(final InetSocketAddress address) {
        // NetconfClientConfiguration has mostly final methods, making it un-mock-able

        final NetconfClientConfiguration.NetconfClientProtocol protocol =
                NetconfClientConfiguration.NetconfClientProtocol.SSH;
        final NetconfHelloMessageAdditionalHeader additionalHeader = mock(NetconfHelloMessageAdditionalHeader.class);
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final ReconnectStrategy reconnectStrategy = mock(ReconnectStrategy.class);
        final AuthenticationHandler authHandler = mock(AuthenticationHandler.class);

        return NetconfClientConfigurationBuilder.create().withProtocol(protocol).withAddress(address)
                .withConnectionTimeoutMillis(0).withAdditionalHeader(additionalHeader)
                .withSessionListener(sessionListener).withReconnectStrategy(reconnectStrategy)
                .withAuthHandler(authHandler).build();
    }

    @Test
    public void canCreateASessionFromAConfiguration() {
        // given
        final CallHomeMountSessionContext mockContext = mock(CallHomeMountSessionContext.class);
        final InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        doReturn(mockContext).when(mockSessMgr).getByAddress(eq(someAddress));

        final NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        instance.createClient(someCfg);
        // then
        verify(mockContext, times(1)).activateNetconfChannel(any(NetconfClientSessionListener.class));
    }

    @Test
    public void noSessionIsCreatedWithoutAContextAvailableForAGivenAddress() {
        // given
        final InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        final NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        final Future<NetconfClientSession> future = instance.createClient(someCfg);
        // then
        assertFalse(future.isSuccess());
    }

    @Test
    public void nodeIsInsertedIntoTopologyWhenSubsystemIsOpened() throws UnknownHostException {
        // given
        final NodeId mockNodeId = mock(NodeId.class);
        final Node mockNode = mock(Node.class);
        final CallHomeMountSessionContext mockDevCtxt = mock(CallHomeMountSessionContext.class);
        doReturn(mockNodeId).when(mockDevCtxt).getId();
        doReturn(mockNode).when(mockDevCtxt).getConfigNode();
        doReturn(mockDevCtxt).when(mockSessMgr).createSession(any(CallHomeProtocolSessionContext.class),
                any(CallHomeChannelActivator.class), any(CallHomeMountSessionContext.CloseCallback.class));
        final CallHomeChannelActivator activator = mock(CallHomeChannelActivator.class);
        instance.createTopology();
        // when
        instance.onNetconfSubsystemOpened(mockProtoSess, activator);
        // then
        verify(instance.topology, times(1)).connectNode(any(NodeId.class), any(Node.class));
    }
}
