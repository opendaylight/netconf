/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mapping.operations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.NetconfServerSessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;

@ExtendWith(MockitoExtension.class)
class DefaultStopExiTest {
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private NetconfServerSessionListener sessionListener;
    @Mock
    private MessageDecoder decoder;
    @Mock
    private MessageWriter messageWriter;

    @Test
    void testHandleWithNoSubsequentOperations() throws Exception {
        final DefaultStopExi exi = new DefaultStopExi(new SessionIdType(Uint32.ONE));
        final Document doc = XmlUtil.newDocument();
        final var encoder = new MessageEncoder(messageWriter);
        doReturn(pipeline).when(channel).pipeline();
        doReturn(decoder).when(pipeline).replace(eq(MessageDecoder.class), anyString(), any(MessageDecoder.class));
        doReturn(encoder).when(pipeline).get(MessageEncoder.class);

        exi.setNetconfSession(new NetconfServerSession(sessionListener, channel, new SessionIdType(Uint32.TWO), null));

        assertNotNull(exi.handleWithNoSubsequentOperations(doc,
                XmlElement.fromDomElement(XmlUtil.readXmlToElement("<elem/>"))));
        verify(pipeline, times(1)).replace(eq(MessageDecoder.class), anyString(), any(MessageDecoder.class));
    }
}
