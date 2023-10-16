/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.junit.Test;

public class TcpClientChannelInitializerTest {
    @Test
    public void testInitializeSessionNegotiator() throws Exception {
        final var factory = mock(NetconfClientSessionNegotiatorFactory.class);
        final var sessionNegotiator = mock(NetconfClientSessionNegotiator.class);
        doReturn("").when(sessionNegotiator).toString();
        doReturn(sessionNegotiator).when(factory).getSessionNegotiator(any(), any(), any());
        final var listener = mock(NetconfClientSessionListener.class);
        final var initializer = new TcpClientChannelInitializer(factory, listener);
        final var pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        final var channel = mock(Channel.class);
        doReturn(pipeline).when(channel).pipeline();
        doReturn("").when(channel).toString();

        final var channelConfig = mock(ChannelConfig.class);
        doReturn(channelConfig).when(channel).config();
        doReturn(1L).when(factory).getConnectionTimeoutMillis();
        doReturn(channelConfig).when(channelConfig).setConnectTimeoutMillis(1);

        initializer.initializeSessionNegotiator(channel, SettableFuture.create());
        verify(pipeline, times(1)).addAfter(anyString(), anyString(), any(ChannelHandler.class));
    }
}
