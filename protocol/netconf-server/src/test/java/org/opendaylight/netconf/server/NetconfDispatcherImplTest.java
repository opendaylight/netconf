/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import java.net.InetSocketAddress;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yangtools.concepts.Registration;

public class NetconfDispatcherImplTest {
    private EventLoopGroup nettyGroup;
    private NetconfServerDispatcherImpl dispatch;
    private HashedWheelTimer hashedWheelTimer;

    @Before
    public void setUp() throws Exception {
        nettyGroup = new NioEventLoopGroup();

        AggregatedNetconfOperationServiceFactory factoriesListener = new AggregatedNetconfOperationServiceFactory();

        SessionIdProvider idProvider = new DefaultSessionIdProvider();
        hashedWheelTimer = new HashedWheelTimer();

        NetconfServerSessionNegotiatorFactory serverNegotiatorFactory =
                new NetconfServerSessionNegotiatorFactoryBuilder()
                        .setAggregatedOpService(factoriesListener)
                        .setTimer(hashedWheelTimer)
                        .setIdProvider(idProvider)
                        .setMonitoringService(createMockedMonitoringService())
                        .setConnectionTimeoutMillis(5000)
                        .build();

        ServerChannelInitializer serverChannelInitializer =
                new ServerChannelInitializer(serverNegotiatorFactory);

        dispatch = new NetconfServerDispatcherImpl(
                serverChannelInitializer, nettyGroup, nettyGroup);
    }

    @After
    public void tearDown() throws Exception {
        hashedWheelTimer.stop();
        nettyGroup.shutdownGracefully();
    }

    @Test
    public void test() throws Exception {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 8333);
        ChannelFuture server = dispatch.createServer(addr);
        server.get();
    }

    private static NetconfMonitoringService createMockedMonitoringService() {
        final NetconfMonitoringService monitoring = mock(NetconfMonitoringService.class);
        final SessionListener sessionListener = mock(SessionListener.class);
        doNothing().when(sessionListener).onSessionUp(any(NetconfServerSession.class));
        doNothing().when(sessionListener).onSessionDown(any(NetconfServerSession.class));
        doNothing().when(sessionListener).onSessionEvent(any(SessionEvent.class));
        doReturn((Registration) () -> { }).when(monitoring)
            .registerCapabilitiesListener(any(NetconfMonitoringService.CapabilitiesListener.class));
        doReturn(sessionListener).when(monitoring).getSessionListener();
        doReturn(new CapabilitiesBuilder().setCapability(Set.of()).build()).when(monitoring).getCapabilities();
        return monitoring;
    }
}
