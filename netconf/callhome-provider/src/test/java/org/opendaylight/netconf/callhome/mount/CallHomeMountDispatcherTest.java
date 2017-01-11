/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.protocol.framework.ReconnectStrategy;

public class CallHomeMountDispatcherTest {
    private String topologyId;
    private BindingAwareBroker mockBroker;
    private EventExecutor mockExecutor;
    private ScheduledThreadPool mockKeepAlive;
    private ThreadPool mockProcessingExecutor;
    private SchemaRepositoryProvider mockSchemaRepoProvider;
    private Broker mockDomBroker;

    private CallHomeMountDispatcher instance;
    private DataBroker mockDataBroker;
    private DOMMountPointService mockMount;

    @Before
    public void setup() {
        topologyId = "";
        mockBroker = mock(BindingAwareBroker.class);
        mockExecutor = mock(EventExecutor.class);
        mockKeepAlive = mock(ScheduledThreadPool.class);
        mockProcessingExecutor = mock(ThreadPool.class);
        mockSchemaRepoProvider = mock(SchemaRepositoryProvider.class);
        mockDomBroker = mock(Broker.class);
        mockDataBroker = mock(DataBroker.class);
        mockMount = mock(DOMMountPointService.class);
        instance = new CallHomeMountDispatcher(topologyId, mockBroker, mockExecutor, mockKeepAlive,
                mockProcessingExecutor, mockSchemaRepoProvider, mockDomBroker, mockDataBroker, mockMount);
    }

    NetconfClientConfiguration someConfiguration(InetSocketAddress address) {
        // NetconfClientConfiguration has mostly final methods, making it un-mock-able

        NetconfClientConfiguration.NetconfClientProtocol protocol =
                NetconfClientConfiguration.NetconfClientProtocol.SSH;
        NetconfHelloMessageAdditionalHeader additionalHeader = mock(NetconfHelloMessageAdditionalHeader.class);
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        ReconnectStrategy reconnectStrategy = mock(ReconnectStrategy.class);
        AuthenticationHandler authHandler = mock(AuthenticationHandler.class);

        return NetconfClientConfigurationBuilder.create().withProtocol(protocol).withAddress(address)
                .withConnectionTimeoutMillis(0).withAdditionalHeader(additionalHeader)
                .withSessionListener(sessionListener).withReconnectStrategy(reconnectStrategy)
                .withAuthHandler(authHandler).build();
    }

    @Test
    public void canCreateASessionFromAConfiguration() {
        // given
        CallHomeMountSessionContext mockContext = mock(CallHomeMountSessionContext.class);
        InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        // instance.contextByAddress.put(someAddress, mockContext);

        NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        instance.createClient(someCfg);
        // then
        // verify(mockContext, times(1)).activate(any(NetconfClientSessionListener.class));
    }

    @Test
    public void noSessionIsCreatedWithoutAContextAvailableForAGivenAddress() {
        // given
        InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        Future<NetconfClientSession> future = instance.createClient(someCfg);
        // then
        assertFalse(future.isSuccess());
    }

    @Test
    public void nodeIsInsertedIntoTopologyWhenSubsystemIsOpened() throws UnknownHostException {
        // given
        InetSocketAddress someAddress = new InetSocketAddress(InetAddress.getByName("1.2.3.4"), 123);
        CallHomeChannelActivator activator = mock(CallHomeChannelActivator.class);
        // instance.topology = mock(CallHomeTopology.class);
        // when
        // instance.onNetconfSubsystemOpened(someAddress, activator);
        // then
        // verify(instance.topology, times(1)).connectNode(any(NodeId.class), any(Node.class));
    }
}
