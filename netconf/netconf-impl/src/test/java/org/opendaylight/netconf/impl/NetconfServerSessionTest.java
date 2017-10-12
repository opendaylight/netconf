/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToEXIEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.NetconfTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.openexi.proc.common.EXIOptions;
import org.w3c.dom.Document;

public class NetconfServerSessionTest {

    private static final String HOST = "127.0.0.1";
    private static final String PORT = "17830";
    private static final String SSH_TRANSPORT = "ssh";
    private static final String TCP_TRANSPORT = "tcp";
    private static final String SESSION_ID = "1";
    private static final String USER = "admin";
    private NetconfServerSession session;
    private EmbeddedChannel channel;
    private NetconfMessage msg;
    @Mock
    private NetconfServerSessionListener listener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, SSH_TRANSPORT, SESSION_ID);
        channel = new EmbeddedChannel();
        session = new NetconfServerSession(listener, channel, 1L, header);
        doNothing().when(listener).onSessionUp(any());
        msg = new NetconfMessage(XmlUtil.readXmlToDocument("<rpc-reply></rpc-reply>"));
    }

    @Test
    public void testSessionUp() throws Exception {
        session.sessionUp();
        verify(listener).onSessionUp(session);
    }

    @Test
    public void testDelayedClose() throws Exception {
        doNothing().when(listener).onSessionTerminated(eq(session), any());
        session.delayedClose();
        session.sendMessage(msg);
        channel.runPendingTasks();
        final Object o = channel.readOutbound();
        Assert.assertEquals(msg, o);
        verify(listener).onSessionTerminated(eq(session), any());
    }

    @Test
    public void testSendMessage() throws Exception {
        session.sendMessage(msg);
        channel.runPendingTasks();
        final Object o = channel.readOutbound();
        Assert.assertEquals(msg, o);
    }

    @Test
    public void testSendNotification() throws Exception {
        doNothing().when(listener).onNotification(any(), any());
        final Document msgDoc = XmlUtil.readXmlToDocument("<notification></notification>");
        final NetconfNotification notif = new NetconfNotification(msgDoc);
        session.sendMessage(notif);
        channel.runPendingTasks();
        final Object o = channel.readOutbound();
        Assert.assertEquals(notif, o);
        verify(listener).onNotification(session, notif);
    }

    @Test
    public void testOnIncommingRpcSuccess() throws Exception {
        session.sessionUp();
        final Session managementSession = this.session.toManagementSession();
        this.session.onIncommingRpcSuccess();
        final Session afterRpcSuccess = this.session.toManagementSession();
        Assert.assertEquals(managementSession.getInRpcs().getValue() + 1,
                afterRpcSuccess.getInRpcs().getValue().longValue());
    }

    @Test
    public void testOnIncommingRpcFail() throws Exception {
        session.sessionUp();
        final Session managementSession = this.session.toManagementSession();
        this.session.onIncommingRpcFail();
        final Session afterRpcSuccess = this.session.toManagementSession();
        Assert.assertEquals(managementSession.getInBadRpcs().getValue() + 1,
                afterRpcSuccess.getInBadRpcs().getValue().longValue());
    }

    @Test
    public void testOnOutgoingRpcError() throws Exception {
        session.sessionUp();
        final Session managementSession = this.session.toManagementSession();
        this.session.onOutgoingRpcError();
        final Session afterRpcSuccess = this.session.toManagementSession();
        Assert.assertEquals(managementSession.getOutRpcErrors().getValue() + 1,
                afterRpcSuccess.getOutRpcErrors().getValue().longValue());
    }

    @Test
    public void testToManagementSession() throws Exception {
        final NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, TCP_TRANSPORT, SESSION_ID);
        final EmbeddedChannel ch = new EmbeddedChannel();
        final NetconfServerSession tcpSession = new NetconfServerSession(listener, ch, 1L, header);
        tcpSession.sessionUp();
        final Session managementSession = tcpSession.toManagementSession();
        Assert.assertEquals(new String(managementSession.getSourceHost().getValue()), HOST);
        Assert.assertEquals(managementSession.getUsername(), USER);
        Assert.assertEquals(managementSession.getSessionId().toString(), SESSION_ID);
        Assert.assertEquals(managementSession.getTransport(), NetconfTcp.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToManagementSessionUnknownTransport() throws Exception {
        final NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, "http", SESSION_ID);
        final EmbeddedChannel ch = new EmbeddedChannel();
        final NetconfServerSession tcpSession = new NetconfServerSession(listener, ch, 1L, header);
        tcpSession.sessionUp();
        tcpSession.toManagementSession();
    }

    @Test
    public void testToManagementSessionIpv6() throws Exception {
        final NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader(USER, "::1", PORT, SSH_TRANSPORT, SESSION_ID);
        final EmbeddedChannel ch = new EmbeddedChannel();
        final NetconfServerSession tcpSession = new NetconfServerSession(listener, ch, 1L, header);
        tcpSession.sessionUp();
        final Session managementSession = tcpSession.toManagementSession();
        Assert.assertEquals(new String(managementSession.getSourceHost().getValue()), "::1");
        Assert.assertEquals(managementSession.getUsername(), USER);
        Assert.assertEquals(managementSession.getSessionId().toString(), SESSION_ID);
        Assert.assertEquals(managementSession.getTransport(), NetconfSsh.class);
    }

    @Test
    public void testThisInstance() throws Exception {
        Assert.assertEquals(session, session.thisInstance());
    }

    @Test
    public void testAddExiHandlers() throws Exception {
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
                new NetconfXMLToMessageDecoder());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new NetconfMessageToXMLEncoder());
        final NetconfEXICodec codec = new NetconfEXICodec(new EXIOptions());
        session.addExiHandlers(NetconfEXIToMessageDecoder.create(codec), NetconfMessageToEXIEncoder.create(codec));
    }

    @Test
    public void testStopExiCommunication() throws Exception {
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
                new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new ChannelOutboundHandlerAdapter());
        session.stopExiCommunication();
        //handler is replaced only after next send message call
        final ChannelHandler exiEncoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER);
        Assert.assertTrue(ChannelOutboundHandlerAdapter.class.equals(exiEncoder.getClass()));
        session.sendMessage(msg);
        channel.runPendingTasks();
        final ChannelHandler decoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER);
        Assert.assertTrue(NetconfXMLToMessageDecoder.class.equals(decoder.getClass()));
        final ChannelHandler encoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER);
        Assert.assertTrue(NetconfMessageToXMLEncoder.class.equals(encoder.getClass()));
    }

}
