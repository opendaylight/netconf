/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;


@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class EncryptedPasswordChangeListenerTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");

    @Mock
    private DataBroker dataBroker;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private WriteTransaction writeTransaction;
    @Captor
    private ArgumentCaptor<NetconfNode> nodeCaptor;
    @Captor
    private ArgumentCaptor<InstanceIdentifier> iiCaptor;
    private EncryptedPasswordChangeListener listener;

    @Before
    public void setUp() throws Exception {
        doReturn(mock(ListenerRegistration.class)).when(dataBroker)
            .registerDataTreeChangeListener(any(DataTreeIdentifier.class), any(
            DataTreeChangeListener.class));
        listener = new EncryptedPasswordChangeListener(dataBroker, encryptionService);
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();
    }

    @Test
    public void testUpdateDatastoreWithEncryptedPassword() {
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        final DataObjectModification<Node> newNode = mock(DataObjectModification.class);
        final DataObjectModification<NetconfNode> mockNetconfNode = mock(DataObjectModification.class);
        doReturn(mockNetconfNode).when(newNode).getModifiedAugmentation(NetconfNode.class);

        final NodeKey key = new NodeKey(NODE_ID);

        final NodeBuilder on = new NodeBuilder()
            .withKey(key)
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setTcpOnly(true)
                .setCredentials(new LoginPasswordBuilder()
                    .setUsername("testuser")
                    .setPassword("test")
                    .build())
                .build());
        doReturn(on.build().augmentation(NetconfNode.class)).when(mockNetconfNode).getDataBefore();

        final NodeBuilder nn = new NodeBuilder()
            .withKey(key)
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setTcpOnly(true)
                .setCredentials(new LoginPasswordBuilder()
                    .setUsername("testuser")
                    .setPassword("test")
                    .build())
                .build());
        doReturn(nn.build().augmentation(NetconfNode.class)).when(mockNetconfNode).getDataAfter();
        doReturn(nn.build()).when(newNode).getDataAfter();

        final Collection<DataTreeModification<Node>> changes = new HashSet<>();
        final DataTreeModification<Node> ch = mock(DataTreeModification.class);
        doReturn(newNode).when(ch).getRootNode();
        changes.add(ch);
        doNothing().when(writeTransaction).mergeParentStructureMerge(any(LogicalDatastoreType.class),
            any(InstanceIdentifier.class), any(NetconfNode.class));
        doReturn("4o9/Hn3Pi4150YrP12N/1g==").when(encryptionService).encrypt("test");

        listener.onDataTreeChanged(changes);

        verify(writeTransaction).mergeParentStructureMerge(eq(LogicalDatastoreType.CONFIGURATION),
            iiCaptor.capture(), nodeCaptor.capture());
        final NetconfNode capturedNode = nodeCaptor.getValue();
        final String encryptedPassword = ((LoginPw)capturedNode.getCredentials()).getLoginPassword().getPassword();
        assertEquals("4o9/Hn3Pi4150YrP12N/1g==", encryptedPassword);
    }
}
