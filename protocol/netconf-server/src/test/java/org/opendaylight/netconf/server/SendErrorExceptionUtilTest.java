/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SendErrorExceptionUtilTest {
    private final DocumentedException exception = new DocumentedException("err");

    @Mock
    NetconfSession netconfSession;
    @Mock
    ChannelFuture channelFuture;
    @Mock
    Channel channel;

    @Before
    public void setUp() throws Exception {
        doReturn(channelFuture).when(netconfSession).sendMessage(any(NetconfMessage.class));
        doReturn(channelFuture).when(channelFuture).addListener(any(GenericFutureListener.class));
        doReturn(channelFuture).when(channel).writeAndFlush(any(NetconfMessage.class));
    }

    @Test
    public void testSendErrorMessage1() throws Exception {
        SendErrorExceptionUtil.sendErrorMessage(netconfSession, exception);
        verify(channelFuture).addListener(any(GenericFutureListener.class));
        verify(netconfSession).sendMessage(any(NetconfMessage.class));
    }

    @Test
    public void testSendErrorMessage2() throws Exception {
        SendErrorExceptionUtil.sendErrorMessage(channel, exception);
        verify(channelFuture).addListener(any(GenericFutureListener.class));
    }

    @Test
    public void testSendErrorMessage3() throws Exception {
        SendErrorExceptionUtil.sendErrorMessage(netconfSession, exception, readMessage("rpc.xml"));
        verify(channelFuture).addListener(any(GenericFutureListener.class));
    }

    @Test
    public void testSendErrorMessage4() throws Exception {
        SendErrorExceptionUtil.sendErrorMessage(netconfSession, exception, readMessage("rpc_ns.xml"));
        final ArgumentCaptor<NetconfMessage> messageCaptor = ArgumentCaptor.forClass(NetconfMessage.class);
        verify(netconfSession, times(1)).sendMessage(messageCaptor.capture());
        final Element rpcReply = messageCaptor.getValue().getDocument().getDocumentElement();
        assertEquals("Invalid value of message-id attribute in the reply message", "a",
            rpcReply.getAttribute("message-id"));
    }

    private static NetconfMessage readMessage(final String name) throws IOException, SAXException {
        try (InputStream resource =
                requireNonNull(SendErrorExceptionUtilTest.class.getResourceAsStream("/messages/" + name))) {
            return new NetconfMessage(XmlUtil.readXmlToDocument(resource));
        }
    }
}
