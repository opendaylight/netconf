/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl.mapping.operations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.NetconfServerSessionListener;
import org.w3c.dom.Document;

public class DefaultCloseSessionTest {

    private static void mockEventLoop(final Channel channel) {
        final EventLoop eventLoop = mock(EventLoop.class);
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
        doReturn(true).when(eventLoop).inEventLoop();
    }

    @Test
    public void testDefaultCloseSession() throws Exception {
        AutoCloseable res = mock(AutoCloseable.class);
        doNothing().when(res).close();
        DefaultCloseSession close = new DefaultCloseSession("", res);
        final Document doc = XmlUtil.newDocument();
        final XmlElement elem = XmlElement.fromDomElement(XmlUtil.readXmlToElement("<elem/>"));
        final Channel channel = mock(Channel.class);
        doReturn("channel").when(channel).toString();
        mockEventLoop(channel);
        final ChannelFuture channelFuture = mock(ChannelFuture.class);
        doReturn(channelFuture).when(channel).close();
        doReturn(channelFuture).when(channelFuture).addListener(any(GenericFutureListener.class));

        final ChannelPromise sendFuture = mock(ChannelPromise.class);
        doAnswer(invocation -> {
            invocation.<GenericFutureListener>getArgument(0).operationComplete(sendFuture);
            return null;
        }).when(sendFuture).addListener(any(GenericFutureListener.class));
        doReturn(sendFuture).when(channel).newPromise();
        doReturn(sendFuture).when(channel).writeAndFlush(anyObject(), anyObject());
        doReturn(true).when(sendFuture).isSuccess();
        final NetconfServerSessionListener listener = mock(NetconfServerSessionListener.class);
        doNothing().when(listener).onSessionTerminated(any(NetconfServerSession.class),
                any(NetconfTerminationReason.class));
        final NetconfServerSession session =
                new NetconfServerSession(listener, channel, 1L,
                        NetconfHelloMessageAdditionalHeader.fromString("[netconf;10.12.0.102:48528;ssh;;;;;;]"));
        close.setNetconfSession(session);
        close.handleWithNoSubsequentOperations(doc, elem);
        // Fake close response to trigger delayed close
        session.sendMessage(new NetconfMessage(XmlUtil.readXmlToDocument("<rpc-reply message-id=\"101\"\n"
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<ok/>\n"
                + "</rpc-reply>")));
        verify(channel).close();
        verify(listener).onSessionTerminated(any(NetconfServerSession.class), any(NetconfTerminationReason.class));
    }

    @Test(expected = DocumentedException.class)
    public void testDefaultCloseSession2() throws Exception {
        AutoCloseable res = mock(AutoCloseable.class);
        doThrow(NetconfDocumentedException.class).when(res).close();
        DefaultCloseSession session = new DefaultCloseSession("", res);
        XmlElement elem = XmlElement.fromDomElement(XmlUtil.readXmlToElement("<elem/>"));
        session.handleWithNoSubsequentOperations(XmlUtil.newDocument(), elem);
    }
}
