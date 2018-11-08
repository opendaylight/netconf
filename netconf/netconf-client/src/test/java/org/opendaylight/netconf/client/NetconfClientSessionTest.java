/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToEXIEncoder;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;

public class NetconfClientSessionTest {

    @Mock
    ChannelHandler channelHandler;

    @Mock
    Channel channel;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testNetconfClientSession() throws Exception {
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final long sessId = 20L;
        final Collection<String> caps = Lists.newArrayList("cap1", "cap2");

        final NetconfEXICodec codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        final ChannelPipeline pipeline = mock(ChannelPipeline.class);

        Mockito.doReturn(pipeline).when(channel).pipeline();
        Mockito.doReturn(channelHandler).when(pipeline).replace(anyString(), anyString(), any(ChannelHandler.class));
        Mockito.doReturn("").when(channelHandler).toString();

        final NetconfClientSession session = new NetconfClientSession(sessionListener, channel, sessId, caps);
        final NetconfMessageToEXIEncoder exiEncoder = NetconfMessageToEXIEncoder.create(codec);
        final NetconfEXIToMessageDecoder exiDecoder = NetconfEXIToMessageDecoder.create(codec);
        session.addExiHandlers(exiDecoder, exiEncoder);
        session.stopExiCommunication();

        assertEquals(caps, session.getServerCapabilities());
        assertEquals(session, session.thisInstance());

        Mockito.verify(pipeline, Mockito.times(4)).replace(anyString(), anyString(), Mockito.any(ChannelHandler.class));
    }
}
