/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
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
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private MessageWriter messageWriter;

    @Test
    void testNetconfClientSession() throws Exception {
        final var sessId = new SessionIdType(Uint32.valueOf(20));
        final var caps = List.of("cap1", "cap2");
        final var codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        final var encoder = new MessageEncoder(messageWriter);

        doReturn(pipeline).when(channel).pipeline();
        doReturn(channelHandler).when(pipeline).replace(eq(MessageDecoder.class), anyString(),
            any(MessageDecoder.class));
        doReturn(encoder).when(pipeline).get(MessageEncoder.class);

        final var session = new NetconfClientSession(sessionListener, channel, sessId, caps);
        session.addExiHandlers(codec.newMessageDecoder(), codec.newMessageWriter());
        final var started = encoder.writer();
        assertNotSame(messageWriter, started);

        session.stopExiCommunication();
        final var stopped = encoder.writer();
        assertNotSame(messageWriter, stopped);
        assertNotSame(started, stopped);

        assertEquals(caps, session.getServerCapabilities());
        assertEquals(session, session.thisInstance());

        verify(pipeline, times(2)).replace(eq(MessageDecoder.class), anyString(), any(MessageDecoder.class));
    }
}
