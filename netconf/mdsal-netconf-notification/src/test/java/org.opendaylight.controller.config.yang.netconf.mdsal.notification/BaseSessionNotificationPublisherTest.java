/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class BaseSessionNotificationPublisherTest {

    private BaseSessionNotificationPublisher publisher;
    @Mock
    private BaseNotificationPublisherRegistration registration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        publisher = new BaseSessionNotificationPublisher(registration);
        doNothing().when(registration).onSessionStarted(any());
        doNothing().when(registration).onSessionEnded(any());
    }

    private Session createSession(long id) {
        return new SessionBuilder()
                .setSessionId(id)
                .setSourceHost(new Host("0.0.0.0".toCharArray()))
                .setUsername("user")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    public <T> void testOnDataChangedSessionCreated() throws Exception {
        AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> data = mock(AsyncDataChangeEvent.class);
        Session session1 = createSession(1);
        Session session2 = createSession(2);
        List<Session> sessionList = new ArrayList<>();
        sessionList.add(session1);
        sessionList.add(session2);
        Sessions originalSubtree = new SessionsBuilder()
                .setSession(Collections.singletonList(session1))
                .build();
        Sessions updatedSubtree = new SessionsBuilder()
                .setSession(sessionList)
                .build();
        doReturn(originalSubtree).when(data).getOriginalSubtree();
        doReturn(updatedSubtree).when(data).getUpdatedSubtree();
        doReturn(Collections.emptyMap()).when(data).getCreatedData();

        publisher.onDataChanged(data);
        ArgumentCaptor<NetconfSessionStart> captor = ArgumentCaptor.forClass(NetconfSessionStart.class);
        verify(registration).onSessionStarted(captor.capture());
        final NetconfSessionStart value = captor.getValue();
        Assert.assertEquals(session2.getSessionId(), value.getSessionId().getValue());
        Assert.assertEquals(session2.getSourceHost().getIpAddress(), value.getSourceHost());
        Assert.assertEquals(session2.getUsername(), value.getUsername());
    }

    @Test
    @SuppressWarnings("unchecked")
    public <T> void testOnDataChangedSessionDeleted() throws Exception {
        AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> data = mock(AsyncDataChangeEvent.class);
        Session session1 = createSession(1);
        Session session2 = createSession(2);
        List<Session> sessionList = new ArrayList<>();
        sessionList.add(session1);
        sessionList.add(session2);
        Sessions originalSubtree = new SessionsBuilder()
                .setSession(sessionList)
                .build();
        Sessions updatedSubtree = new SessionsBuilder()
                .setSession(Collections.singletonList(session1))
                .build();
        doReturn(originalSubtree).when(data).getOriginalSubtree();
        doReturn(updatedSubtree).when(data).getUpdatedSubtree();
        doReturn(Collections.emptyMap()).when(data).getCreatedData();

        publisher.onDataChanged(data);
        ArgumentCaptor<NetconfSessionEnd> captor = ArgumentCaptor.forClass(NetconfSessionEnd.class);
        verify(registration).onSessionEnded(captor.capture());
        final NetconfSessionEnd value = captor.getValue();
        Assert.assertEquals(session2.getSessionId(), value.getSessionId().getValue());
        Assert.assertEquals(session2.getSourceHost().getIpAddress(), value.getSourceHost());
        Assert.assertEquals(session2.getUsername(), value.getUsername());
    }

}