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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToEXIEncoder;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfClientSessionTest {

    @Mock
    ChannelHandler channelHandler;

    @Mock
    Channel channel;

    @Test
    public void testNetconfClientSession() throws Exception {
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final var sessId = new SessionIdType(Uint32.valueOf(20));
        final var caps = List.of("cap1", "cap2");

        final NetconfEXICodec codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        final ChannelPipeline pipeline = mock(ChannelPipeline.class);

        Mockito.doReturn(pipeline).when(channel).pipeline();
        Mockito.doReturn(channelHandler).when(pipeline).replace(anyString(), anyString(), any(ChannelHandler.class));

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
