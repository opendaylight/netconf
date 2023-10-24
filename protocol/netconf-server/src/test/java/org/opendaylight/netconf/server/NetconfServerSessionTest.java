/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToEXIEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
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
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, SSH_TRANSPORT, SESSION_ID);
        channel = new EmbeddedChannel();
        session = new NetconfServerSession(listener, channel, new SessionIdType(Uint32.ONE), header);
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
        assertEquals(msg, channel.readOutbound());
        verify(listener).onSessionTerminated(eq(session), any());
    }

    @Test
    public void testSendMessage() throws Exception {
        session.sendMessage(msg);
        channel.runPendingTasks();
        assertEquals(msg, channel.readOutbound());
    }

    @Test
    public void testSendNotification() throws Exception {
        doNothing().when(listener).onNotification(any(), any());
        final var msgDoc = XmlUtil.readXmlToDocument("<notification></notification>");
        final var notif = NotificationMessage.ofNotificationContent(msgDoc);
        session.sendMessage(notif);
        channel.runPendingTasks();
        assertEquals(notif, channel.readOutbound());
        verify(listener).onNotification(session, notif);
    }

    @Test
    public void testOnIncommingRpcSuccess() throws Exception {
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onIncommingRpcSuccess();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getInRpcs().getValue().toJava() + 1,
                afterRpcSuccess.getInRpcs().getValue().longValue());
    }

    @Test
    public void testOnIncommingRpcFail() throws Exception {
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onIncommingRpcFail();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getInBadRpcs().getValue().toJava() + 1,
                afterRpcSuccess.getInBadRpcs().getValue().longValue());
    }

    @Test
    public void testOnOutgoingRpcError() throws Exception {
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onOutgoingRpcError();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getOutRpcErrors().getValue().toJava() + 1,
                afterRpcSuccess.getOutRpcErrors().getValue().longValue());
    }

    @Test
    public void testToManagementSession() throws Exception {
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, TCP_TRANSPORT, SESSION_ID);
        final var ch = new EmbeddedChannel();
        try (var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header)) {
            tcpSession.sessionUp();
            final var managementSession = tcpSession.toManagementSession();
            assertEquals(HOST, managementSession.getSourceHost().getIpAddress().getIpv4Address().getValue());
            assertEquals(USER, managementSession.getUsername());
            assertEquals(SESSION_ID, managementSession.getSessionId().toString());
            assertEquals(NetconfTcp.VALUE, managementSession.getTransport());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToManagementSessionUnknownTransport() throws Exception {
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, "http", SESSION_ID);
        final var ch = new EmbeddedChannel();
        try (var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header)) {
            tcpSession.sessionUp();
            tcpSession.toManagementSession();
        }
    }

    @Test
    public void testToManagementSessionIpv6() throws Exception {
        final var header = new NetconfHelloMessageAdditionalHeader(USER, "::1", PORT, SSH_TRANSPORT, SESSION_ID);
        final var ch = new EmbeddedChannel();
        try (var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header)) {
            tcpSession.sessionUp();
            final Session managementSession = tcpSession.toManagementSession();
            assertEquals("::1", managementSession.getSourceHost().getIpAddress().getIpv6Address().getValue());
            assertEquals(USER, managementSession.getUsername());
            assertEquals(SESSION_ID, managementSession.getSessionId().toString());
            assertEquals(NetconfSsh.VALUE, managementSession.getTransport());
        }
    }

    @Test
    public void testThisInstance() throws Exception {
        assertEquals(session, session.thisInstance());
    }

    @Test
    public void testAddExiHandlers() throws Exception {
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
                new NetconfXMLToMessageDecoder());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new NetconfMessageToXMLEncoder());
        final var codec = NetconfEXICodec.forParameters(EXIParameters.empty());
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
        final var exiEncoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER);
        assertEquals(ChannelOutboundHandlerAdapter.class, exiEncoder.getClass());
        session.sendMessage(msg);
        channel.runPendingTasks();
        final var decoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER);
        assertEquals(NetconfXMLToMessageDecoder.class, decoder.getClass());
        final var encoder = channel.pipeline().get(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER);
        assertEquals(NetconfMessageToXMLEncoder.class, encoder.getClass());
    }
}
