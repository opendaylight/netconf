/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter32;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SessionNotificationProducerTest {
    private static final Logger LOG = LoggerFactory.getLogger(SessionNotificationProducerTest.class);

    @Mock
    private BaseNotificationPublisherRegistration registration;
    @Mock
    private ListenerRegistration<?> listenerRegistration;
    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;

    private SessionNotificationProducer publisher;

    @Before
    public void setUp() {
        doReturn(listenerRegistration).when(dataBroker).registerDataTreeChangeListener(any(DataTreeIdentifier.class),
                any(DataTreeChangeListener.class));
        doNothing().when(registration).onSessionStarted(any());
        doNothing().when(registration).onSessionEnded(any());

        doReturn(registration).when(netconfNotificationCollector).registerBaseNotificationPublisher();

        publisher = new SessionNotificationProducer(netconfNotificationCollector, dataBroker);
    }

    @Test
    public void testOnDataChangedSessionCreated() throws Exception {
        final Session session = createSession(Uint32.ONE);
        final DataTreeModification<Session> treeChange = getTreeModification(session, ModificationType.WRITE);
        publisher.onDataTreeChanged(List.of(treeChange));
        ArgumentCaptor<NetconfSessionStart> captor = ArgumentCaptor.forClass(NetconfSessionStart.class);
        verify(registration).onSessionStarted(captor.capture());
        final NetconfSessionStart value = captor.getValue();
        assertEquals(session.getSessionId(), value.getSessionId().getValue());
        assertEquals(session.getSourceHost().getIpAddress(), value.getSourceHost());
        assertEquals(session.getUsername(), value.getUsername());
    }

    @Test
    public void testOnDataChangedSessionUpdated() throws Exception {
        final DataTreeModification<Session> treeChange = mock(DataTreeModification.class);
        final DataObjectModification<Session> changeObject = mock(DataObjectModification.class);
        final Session sessionBefore = createSessionWithInRpcCount(Uint32.ONE, Uint32.ZERO);
        final Session sessionAfter = createSessionWithInRpcCount(Uint32.ONE, Uint32.ONE);
        doReturn(sessionBefore).when(changeObject).getDataBefore();
        doReturn(sessionAfter).when(changeObject).getDataAfter();
        doReturn(ModificationType.WRITE).when(changeObject).getModificationType();
        doReturn(changeObject).when(treeChange).getRootNode();
        publisher.onDataTreeChanged(List.of(treeChange));
        //session didn't start, only stats changed. No notification should be produced
        verify(registration, never()).onSessionStarted(any());
        verify(registration, never()).onSessionEnded(any());
    }

    @Test
    public void testOnDataChangedSessionDeleted() throws Exception {
        final Session session = createSession(Uint32.ONE);
        final DataTreeModification<Session> data = getTreeModification(session, ModificationType.DELETE);
        publisher.onDataTreeChanged(List.of(data));
        ArgumentCaptor<NetconfSessionEnd> captor = ArgumentCaptor.forClass(NetconfSessionEnd.class);
        verify(registration).onSessionEnded(captor.capture());
        final NetconfSessionEnd value = captor.getValue();
        assertEquals(session.getSessionId(), value.getSessionId().getValue());
        assertEquals(session.getSourceHost().getIpAddress(), value.getSourceHost());
        assertEquals(session.getUsername(), value.getUsername());
    }

    private static Session createSession(final Uint32 id) {
        return createSessionWithInRpcCount(id, Uint32.ZERO);
    }

    private static Session createSessionWithInRpcCount(final Uint32 id, final Uint32 inRpc) {
        return new SessionBuilder()
                .setSessionId(id)
                .setSourceHost(new Host(new IpAddress(new Ipv4Address("0.0.0.0"))))
                .setUsername("user")
                .setInRpcs(new ZeroBasedCounter32(inRpc))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static DataTreeModification<Session> getTreeModification(final Session session,
            final ModificationType type) {
        final DataTreeModification<Session> treeChange = mock(DataTreeModification.class);
        final DataObjectModification<Session> changeObject = mock(DataObjectModification.class);
        switch (type) {
            case WRITE:
                doReturn(null).when(changeObject).getDataBefore();
                doReturn(session).when(changeObject).getDataAfter();
                break;
            case DELETE:
                doReturn(session).when(changeObject).getDataBefore();
                break;
            default:
                LOG.debug("Received intentionally unhandled type: {}.", type);
        }
        doReturn(type).when(changeObject).getModificationType();
        doReturn(changeObject).when(treeChange).getRootNode();
        return treeChange;
    }

}
