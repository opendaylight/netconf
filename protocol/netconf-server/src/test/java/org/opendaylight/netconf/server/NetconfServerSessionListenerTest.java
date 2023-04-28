/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.netty.channel.embedded.EmbeddedChannel;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.osgi.NetconfOperationRouterImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;

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

    @BeforeClass
    public static void classSetUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
    }

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
        final Exception cause = new RuntimeException("cause");
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
        final Document reply = XmlUtil.readXmlToDocument("<rpc-reply message-id=\"101\" "
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><example/></rpc-reply>");
        doReturn(reply).when(router).onNetconfMessage(any(), any());
        final NetconfMessage msg = new NetconfMessage(XmlUtil.readXmlToDocument("<rpc message-id=\"101\" "
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><example/></rpc>"));
        listener.onMessage(session, msg);
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_SUCCESS)));
        channel.runPendingTasks();
        final NetconfMessage sentMsg = channel.readOutbound();
        final Diff diff = XMLUnit.compareXML(reply, sentMsg.getDocument());
        assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void testOnMessageRuntimeFail() throws Exception {
        doThrow(new RuntimeException("runtime fail")).when(router).onNetconfMessage(any(), any());
        final Document reply =
                XmlUtil.readXmlToDocument("<rpc message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                        + "<example/></rpc>");
        final NetconfMessage msg = new NetconfMessage(reply);
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> listener.onMessage(session, msg));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_FAIL)));
    }

    @Test
    public void testOnMessageDocumentedFail() throws Exception {
        final Document reply =
                XmlUtil.readXmlToDocument("<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                        + "<rpc-error>\n"
                        + "<error-type>protocol</error-type>\n"
                        + "<error-tag>unknown-element</error-tag>\n"
                        + "<error-severity>error</error-severity>\n"
                        + "<error-message>Unknown tag bad-rpc in message:\n"
                        + "&lt;bad-rpc/&gt;\n"
                        + "</error-message>\n"
                        + "<error-info>\n"
                        + "<bad-element>bad-rpc</bad-element>\n"
                        + "</error-info>\n"
                        + "</rpc-error>\n"
                        + "</rpc-reply>");
        final NetconfMessage msg = new NetconfMessage(XmlUtil.readXmlToDocument("<bad-rpc/>"));
        listener.onMessage(session, msg);
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.IN_RPC_FAIL)));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.OUT_RPC_ERROR)));
        channel.runPendingTasks();
        final NetconfMessage sentMsg = channel.readOutbound();
        final Diff diff = XMLUnit.compareXML(reply, sentMsg.getDocument());
        assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void testOnNotification() throws Exception {
        listener.onNotification(session, new NotificationMessage(XmlUtil.readXmlToDocument("<notification/>")));
        verify(monitoringListener).onSessionEvent(argThat(sessionEventIs(SessionEvent.Type.NOTIFICATION)));
    }

    private ArgumentMatcher<SessionEvent> sessionEventIs(final SessionEvent.Type type) {
        return event -> event.getType().equals(type) && event.getSession().equals(session);
    }
}
