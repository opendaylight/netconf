/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.osgi.NetconfOperationRouterImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.xmlunit.builder.DiffBuilder;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfServerSessionListenerTest {
    @Mock
    private NetconfOperationRouterImpl router;
    @Mock
    private NetconfMonitoringService monitoring;
    @Mock
    private AutoCloseable closeable;
    @Mock
    private SessionListener monitoringListener;
    private NetconfServerSession session;
    private EmbeddedChannel channel;
    private NetconfServerSessionListener listener;

    @Before
    public void setUp() throws Exception {
        doReturn(monitoringListener).when(monitoring).getSessionListener();
        doNothing().when(monitoringListener).onSessionUp(any());
        doNothing().when(monitoringListener).onSessionDown(any());
        doNothing().when(monitoringListener).onSessionEvent(any());
        channel = new EmbeddedChannel();
        session = new NetconfServerSession(null, channel, new SessionIdType(Uint32.ONE), null);
        listener = new NetconfServerSessionListener(router, monitoring, closeable);
    }

    @Test
    public void testOnSessionUp() throws Exception {
        listener.onSessionUp(session);
        verify(monitoringListener).onSessionUp(session);
    }

    @Test
    public void testOnSessionDown() throws Exception {
        final var cause = new RuntimeException("cause");
        listener.onSessionDown(session, cause);
        verify(monitoringListener).onSessionDown(session);
        verify(closeable).close();
        verify(router).close();
    }

    @Test
    public void testOnSessionTerminated() throws Exception {
        listener.onSessionTerminated(session, new NetconfTerminationReason("reason"));
        verify(monitoringListener).onSessionDown(session);
        verify(closeable).close();
        verify(router).close();
    }

    @Test
    public void testOnMessage() throws Exception {
        final var reply = XmlUtil.readXmlToDocument("<rpc-reply message-id=\"101\" "
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><example/></rpc-reply>");
        doReturn(reply).when(router).onNetconfMessage(any(), any());
        listener.onMessage(session, new NetconfMessage(XmlUtil.readXmlToDocument("<rpc message-id=\"101\" "
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><example/></rpc>")));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_SUCCESS)));
        channel.runPendingTasks();
        final NetconfMessage sentMsg = channel.readOutbound();

        final var diff = DiffBuilder.compare(sentMsg.getDocument())
            .withTest(reply)
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void testOnMessageRuntimeFail() throws Exception {
        doThrow(new RuntimeException("runtime fail")).when(router).onNetconfMessage(any(), any());
        final var msg = new NetconfMessage(XmlUtil.readXmlToDocument(
            "<rpc message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><example/></rpc>"));
        final var ex = assertThrows(IllegalStateException.class, () -> listener.onMessage(session, msg));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_FAIL)));
        assertThat(ex.getMessage(), startsWith("Unable to process incoming message "));
    }

    @Test
    public void testOnMessageDocumentedFail() throws Exception {
        final var msg = new NetconfMessage(XmlUtil.readXmlToDocument("<bad-rpc/>"));
        listener.onMessage(session, msg);
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_FAIL)));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.OUT_RPC_ERROR)));
        channel.runPendingTasks();
        final NetconfMessage sentMsg = channel.readOutbound();

        final var diff = DiffBuilder.compare(sentMsg.getDocument())
            .withTest("""
                <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                  <rpc-error>
                    <error-type>protocol</error-type>
                    <error-tag>unknown-element</error-tag>
                    <error-severity>error</error-severity>
                    <error-message>Unknown tag bad-rpc in message:\n&lt;bad-rpc/&gt;
                    </error-message>
                    <error-info>
                      <bad-element>bad-rpc</bad-element>
                    </error-info>
                   </rpc-error>
                </rpc-reply>""")
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void testOnNotification() throws Exception {
        listener.onNotification(session, NotificationMessage.ofNotificationContent(
            XmlUtil.readXmlToDocument("<notification/>")));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.NOTIFICATION)));
    }

    private ArgumentMatcher<SessionEvent> sessionEventIs(final SessionEvent.Type type) {
        return event -> event.getType().equals(type) && event.getSession().equals(session);
    }
}
