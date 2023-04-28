/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mapping.operations;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;

public class DefaultStopExiTest {
    @Test
    public void testHandleWithNoSubsequentOperations() throws Exception {
        final DefaultStopExi exi = new DefaultStopExi(new SessionIdType(Uint32.ONE));
        final Document doc = XmlUtil.newDocument();
        Channel channel = mock(Channel.class);
        doReturn("mockChannel").when(channel).toString();
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(channel).pipeline();
        ChannelHandler channelHandler = mock(ChannelHandler.class);
        doReturn(channelHandler).when(pipeline).replace(anyString(), anyString(), any(ChannelHandler.class));

        exi.setNetconfSession(new NetconfServerSession(null, channel, new SessionIdType(Uint32.TWO), null));

        assertNotNull(exi.handleWithNoSubsequentOperations(doc,
                XmlElement.fromDomElement(XmlUtil.readXmlToElement("<elem/>"))));
        verify(pipeline, times(1)).replace(anyString(), anyString(), any(ChannelHandler.class));
    }
}
