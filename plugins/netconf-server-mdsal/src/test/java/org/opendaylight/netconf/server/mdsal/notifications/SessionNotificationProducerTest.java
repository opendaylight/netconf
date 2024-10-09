/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
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
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class SessionNotificationProducerTest {
    private static final Logger LOG = LoggerFactory.getLogger(SessionNotificationProducerTest.class);

    @Mock
    private BaseNotificationPublisherRegistration registration;
    @Mock
    private Registration listenerRegistration;
    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DataTreeModification<Session> treeChange;
    @Mock
    private DataObjectModification<Session> changeObject;

    private SessionNotificationProducer publisher;

    @BeforeEach
    void setUp() {
        doReturn(listenerRegistration).when(dataBroker)
            .registerTreeChangeListener(eq(LogicalDatastoreType.OPERATIONAL), any(), any());

        doReturn(registration).when(netconfNotificationCollector).registerBaseNotificationPublisher();

        publisher = new SessionNotificationProducer(netconfNotificationCollector, dataBroker);
    }

    @Test
    void testOnDataChangedSessionCreated() {
        doNothing().when(registration).onSessionStarted(any());
        final var session = createSession(Uint32.ONE);
        final var treeMod = getTreeModification(session, ModificationType.WRITE);
        publisher.onDataTreeChanged(List.of(treeMod));
        final var captor = ArgumentCaptor.forClass(NetconfSessionStart.class);
        verify(registration).onSessionStarted(captor.capture());
        final var value = captor.getValue();
        assertEquals(session.getSessionId(), value.getSessionId().getValue());
        assertEquals(session.getSourceHost().getIpAddress(), value.getSourceHost());
        assertEquals(session.getUsername(), value.getUsername());
    }

    @Test
    void testOnDataChangedSessionUpdated() {
        final var sessionBefore = createSessionWithInRpcCount(Uint32.ONE, Uint32.ZERO);
        final var sessionAfter = createSessionWithInRpcCount(Uint32.ONE, Uint32.ONE);
        doReturn(sessionBefore).when(changeObject).dataBefore();
        doReturn(sessionAfter).when(changeObject).dataAfter();
        doReturn(ModificationType.WRITE).when(changeObject).modificationType();
        doReturn(changeObject).when(treeChange).getRootNode();
        publisher.onDataTreeChanged(List.of(treeChange));
        //session didn't start, only stats changed. No notification should be produced
        verify(registration, never()).onSessionStarted(any());
        verify(registration, never()).onSessionEnded(any());
    }

    @Test
    void testOnDataChangedSessionDeleted() {
        doNothing().when(registration).onSessionEnded(any());
        final var session = createSession(Uint32.ONE);
        final var treeMod = getTreeModification(session, ModificationType.DELETE);
        publisher.onDataTreeChanged(List.of(treeMod));
        final var captor = ArgumentCaptor.forClass(NetconfSessionEnd.class);
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

    private DataTreeModification<Session> getTreeModification(final Session session, final ModificationType type) {
        doReturn(type).when(changeObject).modificationType();
        doReturn(changeObject).when(treeChange).getRootNode();
        return switch (type) {
            case WRITE -> {
                doReturn(null).when(changeObject).dataBefore();
                doReturn(session).when(changeObject).dataAfter();
                yield treeChange;
            }
            case DELETE -> {
                doReturn(session).when(changeObject).dataBefore();
                yield treeChange;
            }
            default -> {
                LOG.debug("Received intentionally unhandled type: {}.", type);
                yield treeChange;
            }
        };
    }
}
