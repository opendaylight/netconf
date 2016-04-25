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

import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;

public class SessionNotificationProducerTest {

    private SessionNotificationProducer publisher;
    @Mock
    private BaseNotificationPublisherRegistration registration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        publisher = new SessionNotificationProducer(registration);
        doNothing().when(registration).onSessionStarted(any());
        doNothing().when(registration).onSessionEnded(any());
    }

    @Test
    public void testOnDataChangedSessionCreated() throws Exception {
        final Session session = createSession(1);
        final DataTreeModification<Session> treeChange = getTreeModification(session, DataObjectModification.ModificationType.WRITE);
        publisher.onDataTreeChanged(Collections.singleton(treeChange));
        ArgumentCaptor<NetconfSessionStart> captor = ArgumentCaptor.forClass(NetconfSessionStart.class);
        verify(registration).onSessionStarted(captor.capture());
        final NetconfSessionStart value = captor.getValue();
        Assert.assertEquals(session.getSessionId(), value.getSessionId().getValue());
        Assert.assertEquals(session.getSourceHost().getIpAddress(), value.getSourceHost());
        Assert.assertEquals(session.getUsername(), value.getUsername());
    }

    @Test
    public void testOnDataChangedSessionDeleted() throws Exception {
        final Session session = createSession(1);
        final DataTreeModification<Session> data = getTreeModification(session, DataObjectModification.ModificationType.DELETE);
        publisher.onDataTreeChanged(Collections.singleton(data));
        ArgumentCaptor<NetconfSessionEnd> captor = ArgumentCaptor.forClass(NetconfSessionEnd.class);
        verify(registration).onSessionEnded(captor.capture());
        final NetconfSessionEnd value = captor.getValue();
        Assert.assertEquals(session.getSessionId(), value.getSessionId().getValue());
        Assert.assertEquals(session.getSourceHost().getIpAddress(), value.getSourceHost());
        Assert.assertEquals(session.getUsername(), value.getUsername());
    }

    private Session createSession(long id) {
        return new SessionBuilder()
                .setSessionId(id)
                .setSourceHost(new Host("0.0.0.0".toCharArray()))
                .setUsername("user")
                .build();
    }

    @SuppressWarnings("unchecked")
    private DataTreeModification<Session> getTreeModification(Session session, DataObjectModification.ModificationType type) {
        final DataTreeModification<Session> treeChange = mock(DataTreeModification.class);
        final DataObjectModification<Session> changeObject = mock(DataObjectModification.class);
        switch (type) {
            case WRITE:
                doReturn(session).when(changeObject).getDataAfter();
                break;
            case DELETE:
                doReturn(session).when(changeObject).getDataBefore();
                break;
        }
        doReturn(type).when(changeObject).getModificationType();
        doReturn(changeObject).when(treeChange).getRootNode();
        return treeChange;
    }

}