/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


@ExtendWith(MockitoExtension.class)
public class EncryptedPasswordChangeListenerTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");

    @Mock
    private DataBroker dataBroker;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private WriteTransaction writeTransaction;
    @Mock
    private DataObjectModification<Node> rootNodeChange;
    @Mock
    private DataObjectModification<NetconfNode> netconfNodeChange;
    @Mock
    private DataTreeModification<Node> change;
    @Mock
    private ListenerRegistration listenerRegistration;
    @Captor
    private ArgumentCaptor<NetconfNode> nodeCaptor;
    @Captor
    private ArgumentCaptor<InstanceIdentifier> iiCaptor;
    private EncryptedPasswordChangeListener listener;

    @BeforeEach
    public void setUp() throws Exception {
        doReturn(listenerRegistration).when(dataBroker)
            .registerDataTreeChangeListener(any(DataTreeIdentifier.class), any(DataTreeChangeListener.class));
        listener = new EncryptedPasswordChangeListener(dataBroker, encryptionService);
        final NodeKey key = new NodeKey(NODE_ID);
        final Node netconfNodeBefore = new NodeBuilder()
            .withKey(key)
            .addAugmentation(new NetconfNodeBuilder()
                .setCredentials(new LoginPwBuilder()
                    .setLoginPassword(new LoginPasswordBuilder()
                        .setUsername("testuser")
                        .setPassword("testtest")
                        .build())
                    .build())
                .build())
            .build();
        final Node netconfNodeAfter = new NodeBuilder()
            .withKey(key)
            .addAugmentation(new NetconfNodeBuilder()
                .setCredentials(new LoginPwBuilder()
                    .setLoginPassword(new LoginPasswordBuilder()
                        .setUsername("testuser")
                        .setPassword("test")
                        .build())
                    .build())
                .build())
            .build();
        doReturn(rootNodeChange).when(change).getRootNode();
        doReturn(netconfNodeChange).when(rootNodeChange).getModifiedAugmentation(NetconfNode.class);
        doReturn(netconfNodeBefore.augmentation(NetconfNode.class)).when(netconfNodeChange).getDataBefore();
        doReturn(netconfNodeAfter.augmentation(NetconfNode.class)).when(netconfNodeChange).getDataAfter();
        lenient().doReturn(netconfNodeAfter).when(rootNodeChange).getDataAfter();
    }

    @Test
    public void testUpdateDatastoreWhenDecryptingPasswordFailed() {
        stubDatastoreAndEncryptionOperations();
        doThrow(IllegalArgumentException.class).when(encryptionService).decrypt("test");

        listener.onDataTreeChanged(Set.of(change));

        verify(writeTransaction).mergeParentStructureMerge(eq(LogicalDatastoreType.CONFIGURATION),
            iiCaptor.capture(), nodeCaptor.capture());
        verifyIdentifier(iiCaptor.getValue());
        verifyNetconfNode(nodeCaptor.getValue());
    }

    @Test
    public void testUpdateDatastoreWhenDecryptingPasswordSuccessful() {
        stubDatastoreAndEncryptionOperations();
        doReturn("test").when(encryptionService).decrypt("test");

        listener.onDataTreeChanged(Set.of(change));

        verify(writeTransaction).mergeParentStructureMerge(eq(LogicalDatastoreType.CONFIGURATION),
            iiCaptor.capture(), nodeCaptor.capture());
        verifyIdentifier(iiCaptor.getValue());
        verifyNetconfNode(nodeCaptor.getValue());
    }

    @Test
    public void testSkipUpdatingDatastoreWhenPasswordAlreadyEncrypted() {
        doReturn("decryptedPassword").when(encryptionService).decrypt("test");
        listener.onDataTreeChanged(Set.of(change));
        verify(dataBroker, never()).newWriteOnlyTransaction();
    }

    private static void verifyNetconfNode(final NetconfNode node) {
        final String encryptedPassword = ((LoginPw)node.getCredentials()).getLoginPassword().getPassword();
        assertEquals("4o9/Hn3Pi4150YrP12N/1g==", encryptedPassword);
    }

    private static void verifyIdentifier(final InstanceIdentifier identifier) {
        assertNotNull(identifier);
        String nodeIdValue = null;
        for (final Object pathArgument : identifier.getPathArguments()) {
            if (pathArgument instanceof InstanceIdentifier.IdentifiableItem item
                && item.getKey() instanceof NodeKey nodeKey) {
                nodeIdValue = nodeKey.getNodeId().getValue();
                if (!nodeIdValue.isEmpty()) {
                    break;  // Exit the loop once we've found and extracted the NodeId value
                }
            }
        }
        assertEquals("testing-node", nodeIdValue);
    }

    private void stubDatastoreAndEncryptionOperations() {
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTransaction).commit();
        doNothing().when(writeTransaction).mergeParentStructureMerge(any(LogicalDatastoreType.class),
            any(InstanceIdentifier.class), any(NetconfNode.class));
        doReturn("4o9/Hn3Pi4150YrP12N/1g==").when(encryptionService).encrypt("test");
    }
}
