/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfSessionMonitoringServiceTest {
    private static final Session SESSION_1 = new SessionBuilder()
            .setSessionId(Uint32.valueOf(1))
            .setSourceHost(new Host(new IpAddress(new Ipv4Address("0.0.0.0"))))
            .setUsername("admin")
            .build();
    private static final Session SESSION_2 = new SessionBuilder()
            .setSessionId(Uint32.valueOf(2))
            .setSourceHost(new Host(new IpAddress(new Ipv4Address("0.0.0.0"))))
            .setUsername("admin")
            .build();

    @Mock
    private NetconfManagementSession sessionMock1;
    @Mock
    private NetconfManagementSession sessionMock2;
    @Mock
    private NetconfMonitoringService.SessionsListener listener;
    @Mock
    private BaseNotificationPublisherRegistration notificationPublisher;

    private NetconfSessionMonitoringService monitoringService;

    @Before
    public void setUp() {
        doReturn(SESSION_1).when(sessionMock1).toManagementSession();
        doReturn(SESSION_2).when(sessionMock2).toManagementSession();
        doNothing().when(listener).onSessionStarted(any());
        doNothing().when(listener).onSessionEnded(any());

        monitoringService = new NetconfSessionMonitoringService(Optional.empty(), 0);
        monitoringService.registerListener(listener);
    }

    @Test
    public void testListeners() {
        monitoringService.onSessionUp(sessionMock1);
        HashSet<Capability> added = new HashSet<>();
        added.add(new BasicCapability("toAdd"));
        monitoringService.onSessionDown(sessionMock1);
        verify(listener).onSessionStarted(any());
        verify(listener).onSessionEnded(any());
    }

    @Test
    public void testClose() {
        monitoringService.onSessionUp(sessionMock1);
        assertEquals(1, monitoringService.getSessions().nonnullSession().size());
        monitoringService.close();
        assertNull(monitoringService.getSessions().getSession());
    }

    @Test
    public void testOnSessionUpAndDown() {
        monitoringService.onSessionUp(sessionMock1);
        ArgumentCaptor<Session> sessionUpCaptor = ArgumentCaptor.forClass(Session.class);
        verify(listener).onSessionStarted(sessionUpCaptor.capture());
        final Session sesionUp = sessionUpCaptor.getValue();
        assertEquals(SESSION_1.getSessionId(), sesionUp.getSessionId());
        assertEquals(SESSION_1.getSourceHost(), sesionUp.getSourceHost());
        assertEquals(SESSION_1.getUsername(), sesionUp.getUsername());

        monitoringService.onSessionDown(sessionMock1);
        ArgumentCaptor<Session> sessionDownCaptor = ArgumentCaptor.forClass(Session.class);
        verify(listener).onSessionEnded(sessionDownCaptor.capture());
        final Session sessionDown = sessionDownCaptor.getValue();
        assertEquals(SESSION_1.getSessionId(), sessionDown.getSessionId());
        assertEquals(SESSION_1.getSourceHost(), sessionDown.getSourceHost());
        assertEquals(SESSION_1.getUsername(), sessionDown.getUsername());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListenerUpdateSession() {
        ScheduledThreadPool threadPool = mock(ScheduledThreadPool.class);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        doReturn(executor).when(threadPool).getExecutor();
        monitoringService = new NetconfSessionMonitoringService(Optional.of(threadPool), 1);
        monitoringService.registerListener(listener);
        monitoringService.onSessionUp(sessionMock1);
        monitoringService.onSessionUp(sessionMock2);
        monitoringService.onSessionEvent(SessionEvent.inRpcSuccess(sessionMock1));
        ArgumentCaptor<Collection> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(listener, timeout(2000)).onSessionsUpdated(captor.capture());
        final Collection<Session> value = captor.getValue();
        assertTrue(value.contains(SESSION_1));
        assertFalse(value.contains(SESSION_2));
        monitoringService.close();
    }
}
