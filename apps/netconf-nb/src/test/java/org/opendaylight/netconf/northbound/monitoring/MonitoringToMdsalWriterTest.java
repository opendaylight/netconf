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
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
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

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class MonitoringToMdsalWriterTest {
    private static final InstanceIdentifier<NetconfState> INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class);

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

    @Before
    public void setUp() {
        doReturn(capabilityReg).when(monitoring).registerCapabilitiesListener(any());
        doReturn(sessionsReg).when(monitoring).registerSessionsListener(any());

        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();

        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doNothing().when(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL), any());
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();

        writer = new MonitoringToMdsalWriter(dataBroker, monitoring);
    }

    @Test
    public void testClose() throws Exception {
        doNothing().when(capabilityReg).close();
        doNothing().when(sessionsReg).close();
        writer.close();
        InOrder inOrder = inOrder(capabilityReg, sessionsReg, writeTransaction);
        inOrder.verify(sessionsReg).close();
        inOrder.verify(capabilityReg).close();
        inOrder.verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL, INSTANCE_IDENTIFIER);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnCapabilityChanged() throws Exception {
        final InstanceIdentifier<Capabilities> capabilitiesId =
                InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
        final Capabilities capabilities = new CapabilitiesBuilder().build();
        writer.onCapabilitiesChanged(capabilities);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, capabilitiesId, capabilities);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnSchemasChanged() throws Exception {
        final InstanceIdentifier<Schemas> schemasId =
                InstanceIdentifier.create(NetconfState.class).child(Schemas.class);
        final Schemas schemas = new SchemasBuilder().build();
        writer.onSchemasChanged(schemas);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, schemasId, schemas);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnSessionStart() throws Exception {
        Session session = new SessionBuilder()
                .setSessionId(Uint32.ONE)
                .build();
        final InstanceIdentifier<Session> id =
                InstanceIdentifier.create(NetconfState.class)
                        .child(Sessions.class)
                        .child(Session.class, session.key());
        writer.onSessionStarted(session);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, id, session);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnSessionEnd() throws Exception {
        Session session = new SessionBuilder()
                .setSessionId(Uint32.ONE)
                .build();
        final InstanceIdentifier<Session> id =
                InstanceIdentifier.create(NetconfState.class)
                        .child(Sessions.class)
                        .child(Session.class, session.key());
        writer.onSessionEnded(session);
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL, id);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnSessionsUpdated() throws Exception {
        Session session1 = new SessionBuilder()
                .setSessionId(Uint32.ONE)
                .build();
        Session session2 = new SessionBuilder()
                .setSessionId(Uint32.valueOf(2))
                .build();
        final InstanceIdentifier<Session> id1 =
                InstanceIdentifier.create(NetconfState.class)
                        .child(Sessions.class)
                        .child(Session.class, session1.key());
        final InstanceIdentifier<Session> id2 =
                InstanceIdentifier.create(NetconfState.class)
                        .child(Sessions.class)
                        .child(Session.class, session2.key());
        writer.onSessionsUpdated(List.of(session1, session2));
        InOrder inOrder = inOrder(writeTransaction);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, id1, session1);
        inOrder.verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, id2, session2);
        inOrder.verify(writeTransaction).commit();
    }

    @Test
    public void testOnSessionInitiated() throws Exception {
        verify(monitoring).registerCapabilitiesListener(writer);
    }
}