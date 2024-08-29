/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.transport.api.TransportChannel;

@ExtendWith(MockitoExtension.class)
class NetconfChannelListenerTest {
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Spy
    private TransportChannel transportChannel;
    @Spy
    private NetconfChannelListener channelListener;
    @Captor
    private ArgumentCaptor<NetconfChannel> channelCaptor;

    @Test
    void testInit() {
        doReturn(pipeline).when(channel).pipeline();
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));
        doReturn(channel).when(transportChannel).channel();
        doNothing().when(channelListener).onNetconfChannelEstablished(any());

        channelListener.onTransportChannelEstablished(transportChannel);

        verify(pipeline).addLast(anyString(), any(FrameDecoder.class));
        verify(pipeline).addLast(anyString(), any(MessageDecoder.class));
        verify(pipeline).addLast(anyString(), any(MessageEncoder.class));
        verify(channelListener).onNetconfChannelEstablished(channelCaptor.capture());
        assertSame(transportChannel, channelCaptor.getValue().transport());
    }
}
