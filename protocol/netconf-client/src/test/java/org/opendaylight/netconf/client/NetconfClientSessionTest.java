/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NetconfClientSessionTest {
    @Mock
    private ChannelHandler channelHandler;

    @Mock
    private Channel channel;

    @Test
    void testNetconfClientSession() throws Exception {
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final var sessId = new SessionIdType(Uint32.valueOf(20));
        final var caps = List.of("cap1", "cap2");

        final NetconfEXICodec codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        final ChannelPipeline pipeline = mock(ChannelPipeline.class);

        doReturn(pipeline).when(channel).pipeline();
        doReturn(channelHandler).when(pipeline).replace(any(Class.class), anyString(), any(ChannelHandler.class));

        final NetconfClientSession session = new NetconfClientSession(sessionListener, channel, sessId, caps);
        session.addExiHandlers(codec.newMessageDecoder(), codec.newMessageEncoder());
        session.stopExiCommunication();

        assertEquals(caps, session.getServerCapabilities());
        assertEquals(session, session.thisInstance());

        verify(pipeline, Mockito.times(4)).replace(any(Class.class), anyString(), Mockito.any(ChannelHandler.class));
    }
}
