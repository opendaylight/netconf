/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;

public class NetconfClientDispatcherImplTest {
    @Test
    public void testNetconfClientDispatcherImpl() throws Exception {
        EventLoopGroup bossGroup = Mockito.mock(EventLoopGroup.class);
        EventLoopGroup workerGroup = Mockito.mock(EventLoopGroup.class);
        Timer timer = new HashedWheelTimer();

        ChannelFuture chf = Mockito.mock(ChannelFuture.class);
        Channel ch = Mockito.mock(Channel.class);
        doReturn(ch).when(chf).channel();
        Throwable thr = Mockito.mock(Throwable.class);
        doReturn(chf).when(workerGroup).register(any(Channel.class));

        ChannelPromise promise = Mockito.mock(ChannelPromise.class);
        doReturn(promise).when(chf).addListener(any(GenericFutureListener.class));
        doReturn(thr).when(chf).cause();
        doReturn(true).when(chf).isDone();
        doReturn(false).when(chf).isSuccess();

        Long timeout = 200L;
        NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader("a", "host", "port", "trans", "id");
        NetconfClientSessionListener listener = new SimpleNetconfClientSessionListener();
        InetSocketAddress address = InetSocketAddress.createUnresolved("host", 830);
        AuthenticationHandler handler = Mockito.mock(AuthenticationHandler.class);

        doReturn("").when(handler).toString();

        var cfg = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withAuthHandler(handler).build();

        var cfg2 = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withAuthHandler(handler).build();

        NetconfClientDispatcherImpl dispatcher = new NetconfClientDispatcherImpl(bossGroup, workerGroup, timer);
        Future<NetconfClientSession> sshSession = dispatcher.createClient(cfg);
        Future<NetconfClientSession> tcpSession = dispatcher.createClient(cfg2);

        var sshReconn = dispatcher.createClient(cfg);
        final var tcpReconn = dispatcher.createClient(cfg2);

        assertNotNull(sshSession);
        assertNotNull(tcpSession);
        assertNotNull(sshReconn);
        assertNotNull(tcpReconn);

        SslHandlerFactory sslHandlerFactory = Mockito.mock(SslHandlerFactory.class);
        var cfg3 = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withSslHandlerFactory(sslHandlerFactory).build();

        Future<NetconfClientSession> tlsSession = dispatcher.createClient(cfg3);
        var tlsReconn = dispatcher.createClient(cfg3);

        assertNotNull(tlsSession);
        assertNotNull(tlsReconn);
    }
}
