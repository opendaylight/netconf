/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.monitoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SchemasBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class MonitoringToMdsalWriterTest {
    @Mock
    private NetconfMonitoringService monitoring;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;
    @Mock
    private Registration capabilityReg;
    @Mock
    private Registration sessionsReg;

    private MonitoringToMdsalWriter writer;

    @BeforeEach
    public void setUp() {
        doReturn(capabilityReg).when(monitoring).registerCapabilitiesListener(any());
        doReturn(sessionsReg).when(monitoring).registerSessionsListener(any());

        writer = new MonitoringToMdsalWriter(dataBroker, monitoring);
    }

    @Test
    void testClose() {
        doNothing().when(capabilityReg).close();
        doNothing().when(sessionsReg).close();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.close();
        final var inOrder = inOrder(capabilityReg, sessionsReg, writeTransaction);
        inOrder.verify(sessionsReg).close();
        inOrder.verify(capabilityReg).close();
        inOrder.verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetconfState.class));
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnCapabilityChanged() {
        final var capabilitiesId = InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
        final var capabilities = new CapabilitiesBuilder().build();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction)
            .put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any());
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.onCapabilitiesChanged(capabilities);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, capabilitiesId, capabilities);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnSchemasChanged() {
        final var schemasId = InstanceIdentifier.create(NetconfState.class).child(Schemas.class);
        final var schemas = new SchemasBuilder().build();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction)
            .put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any());
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.onSchemasChanged(schemas);
        final var inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, schemasId, schemas);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnSessionStart() {
        final var session = new SessionBuilder().setSessionId(Uint32.ONE).build();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction)
            .put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any());
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.onSessionStarted(session);
        final var inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class, session.key()),
            session);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnSessionEnd() {
        final var session = new SessionBuilder().setSessionId(Uint32.ONE).build();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction)
            .delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.onSessionEnded(session);
        final var inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class, session.key()));
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnSessionsUpdated() {
        final var session1 = new SessionBuilder().setSessionId(Uint32.ONE).build();
        final var session2 = new SessionBuilder().setSessionId(Uint32.TWO).build();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction)
            .put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any());
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();

        writer.onSessionsUpdated(List.of(session1, session2));
        final var inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class, session1.key()),
            session1);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class, session2.key()),
            session2);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    void testOnSessionInitiated() {
        verify(monitoring).registerCapabilitiesListener(writer);
    }
}
