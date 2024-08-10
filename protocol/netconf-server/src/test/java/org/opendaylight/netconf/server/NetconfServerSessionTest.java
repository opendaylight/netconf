/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.codec.XMLMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.XMLMessageWriter;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NetconfServerSessionTest {
    private static final String HOST = "127.0.0.1";
    private static final String PORT = "17830";
    private static final String SSH_TRANSPORT = "ssh";
    private static final String TCP_TRANSPORT = "tcp";
    private static final String SESSION_ID = "1";
    private static final String USER = "admin";

    @Mock
    private NetconfServerSessionListener listener;
    @Spy
    private MessageDecoder decoder;
    @Mock
    private MessageWriter messageWriter;

    private NetconfServerSession session;
    private EmbeddedChannel channel;
    private NetconfMessage msg;

    @BeforeEach
    void setUp() throws Exception {
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, SSH_TRANSPORT, SESSION_ID);
        channel = new EmbeddedChannel();
        session = new NetconfServerSession(listener, channel, new SessionIdType(Uint32.ONE), header);
        msg = new NetconfMessage(XmlUtil.readXmlToDocument("<rpc-reply></rpc-reply>"));
    }

    @Test
    void testSessionUp() {
        doNothing().when(listener).onSessionUp(any());
        session.sessionUp();
        verify(listener).onSessionUp(session);
    }

    @Test
    void testDelayedClose() {
        doNothing().when(listener).onSessionTerminated(eq(session), any());
        session.delayedClose();
        session.sendMessage(msg);
        channel.runPendingTasks();
        assertEquals(msg, channel.readOutbound());
        verify(listener).onSessionTerminated(eq(session), any());
    }

    @Test
    void testSendMessage() {
        session.sendMessage(msg);
        channel.runPendingTasks();
        assertEquals(msg, channel.readOutbound());
    }

    @Test
    void testSendNotification() throws Exception {
        doNothing().when(listener).onNotification(any(), any());
        final var msgDoc = XmlUtil.readXmlToDocument("<notification></notification>");
        final var notif = NotificationMessage.ofNotificationContent(msgDoc);
        session.sendMessage(notif);
        channel.runPendingTasks();
        assertEquals(notif, channel.readOutbound());
        verify(listener).onNotification(session, notif);
    }

    @Test
    void testOnIncommingRpcSuccess() {
        doNothing().when(listener).onSessionUp(any());
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onIncommingRpcSuccess();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getInRpcs().getValue().toJava() + 1,
                afterRpcSuccess.getInRpcs().getValue().longValue());
    }

    @Test
    void testOnIncommingRpcFail() {
        doNothing().when(listener).onSessionUp(any());
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onIncommingRpcFail();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getInBadRpcs().getValue().toJava() + 1,
                afterRpcSuccess.getInBadRpcs().getValue().longValue());
    }

    @Test
    void testOnOutgoingRpcError() {
        doNothing().when(listener).onSessionUp(any());
        session.sessionUp();
        final var managementSession = session.toManagementSession();
        session.onOutgoingRpcError();
        final var afterRpcSuccess = session.toManagementSession();
        assertEquals(managementSession.getOutRpcErrors().getValue().toJava() + 1,
                afterRpcSuccess.getOutRpcErrors().getValue().longValue());
    }

    @Test
    void testToManagementSession() {
        doNothing().when(listener).onSessionUp(any());
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, TCP_TRANSPORT, SESSION_ID);
        final var ch = new EmbeddedChannel();
        final var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header);
        tcpSession.sessionUp();
        final var managementSession = tcpSession.toManagementSession();
        assertEquals(HOST, managementSession.getSourceHost().getIpAddress().getIpv4Address().getValue());
        assertEquals(USER, managementSession.getUsername());
        assertEquals(SESSION_ID, managementSession.getSessionId().toString());
        assertEquals(NetconfTcp.VALUE, managementSession.getTransport());
    }

    @Test
    void testToManagementSessionUnknownTransport() {
        doNothing().when(listener).onSessionUp(any());
        final var header = new NetconfHelloMessageAdditionalHeader(USER, HOST, PORT, "http", SESSION_ID);
        final var ch = new EmbeddedChannel();
        var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header);
        tcpSession.sessionUp();
        assertThrows(IllegalArgumentException.class, tcpSession::toManagementSession);
    }

    @Test
    void testToManagementSessionIpv6() {
        doNothing().when(listener).onSessionUp(any());
        final var header = new NetconfHelloMessageAdditionalHeader(USER, "::1", PORT, SSH_TRANSPORT, SESSION_ID);
        final var ch = new EmbeddedChannel();
        var tcpSession = new NetconfServerSession(listener, ch, new SessionIdType(Uint32.ONE), header);
        tcpSession.sessionUp();
        final var managementSession = tcpSession.toManagementSession();
        assertEquals("::1", managementSession.getSourceHost().getIpAddress().getIpv6Address().getValue());
        assertEquals(USER, managementSession.getUsername());
        assertEquals(SESSION_ID, managementSession.getSessionId().toString());
        assertEquals(NetconfSsh.VALUE, managementSession.getTransport());
    }

    @Test
    void testThisInstance() {
        assertEquals(session, session.thisInstance());
    }

    @Test
    void testAddExiHandlers() throws Exception {
        channel.pipeline().addLast(MessageDecoder.HANDLER_NAME, new XMLMessageDecoder());
        channel.pipeline().addLast("spyEncoder", new MessageEncoder(XMLMessageWriter.of()));
        final var codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        session.addExiHandlers(codec.newMessageDecoder(), codec.newMessageWriter());
    }

    @Test
    void testStopExiCommunication() {
        channel.pipeline().addLast(MessageDecoder.HANDLER_NAME, decoder);
        final var encoder = new MessageEncoder(messageWriter);
        channel.pipeline().addLast("spyEncoder", encoder);

        // handler is replaced only after next send message call
        session.stopExiCommunication();
        assertSame(messageWriter, encoder.writer());

        session.sendMessage(msg);
        channel.runPendingTasks();
        assertInstanceOf(XMLMessageDecoder.class, channel.pipeline().get(MessageDecoder.HANDLER_NAME));
        assertInstanceOf(XMLMessageWriter.class, encoder.writer());
    }
}
