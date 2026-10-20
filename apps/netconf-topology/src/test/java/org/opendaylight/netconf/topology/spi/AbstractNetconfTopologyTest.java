/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.util.concurrent.SettableFuture;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.test.util.CustomTreeModification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.KeyStep;
import org.opendaylight.yangtools.binding.impl.DataObjectIdentifierWithKey;
import org.opendaylight.yangtools.dagger.yang.parser.DaggerDefaultYangParserComponent;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class AbstractNetconfTopologyTest {
    private static final NodeId NODE_ID = new NodeId("nodeId");

    @Mock
    private NetconfTimer timer;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private SslContextFactoryProvider sslContextFactoryProvider;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;
    @Mock
    private NetconfClientFactory clientFactory;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private WriteTransaction wtx;
    @Mock
    private RemoteDeviceHandler delegate;
    @Mock
    private DataObjectWritten<Node> dataObjectWritten;
    @Mock
    private DataObjectDeleted<Node> dataObjectDeleted;
    @Mock
    private DataObjectIdentifierWithKey<Node, NodeKey> path;
    @Mock
    private CallHomeSessionInitializer initializer;
    @Mock
    private CallHomeSessionInitializer otherInitializer;

    @Captor
    private ArgumentCaptor<Throwable> exceptionCaptor;

    @Test
    void hideCredentials() {
        final String userName = "admin";
        final String password = "pa$$word";
        final NetconfNode node = new NetconfNodeBuilder()
            .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
            .setPort(new PortNumber(Uint16.valueOf(9999)))
            .setReconnectOnChangedSchema(true)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setMinBackoffMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setTcpOnly(false)
            .setProtocol(new ProtocolBuilder().setName(Name.TLS).build())
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername(userName)
                    .setPassword(password)
                    .build())
                .build())
            .build();
        final String transformedNetconfNode = AbstractNetconfTopology
            .hideCredentials(node, NodeId.getDefaultInstance("junos"));
        assertTrue(transformedNetconfNode.contains("credentials=***"));
        assertFalse(transformedNetconfNode.contains(userName));
        assertFalse(transformedNetconfNode.contains(password));
    }

    @Test
    void hideNullCredentials() {
        final NetconfNode node = new NetconfNodeBuilder()
            .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
            .setPort(new PortNumber(Uint16.valueOf(9999)))
            .setSchemaless(false)
            .setReconnectOnChangedSchema(false)
            .setMaxConnectionAttempts(Uint32.ZERO)
            .setLockDatastore(true)
            .build();
        assertNotNull(AbstractNetconfTopology.hideCredentials(node, new NodeId("id")));
    }

    @Test
    void testFailToDecryptPassword() throws Exception {
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();
        doNothing().when(wtx).merge(any(), any(DataObjectIdentifier.class), any());

        final var schemaAssembler = new NetconfTopologySchemaAssembler(1);
        final var topology = new TestingNetconfTopologyImpl("id", clientFactory, timer, schemaAssembler,
            schemaManager, dataBroker, mountPointService,
            new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
                sslContextFactoryProvider), deviceActionFactory,
            new DefaultBaseNetconfSchemaProvider(DaggerDefaultYangParserComponent.create().parserFactory()));

        final var netconfNode = new NetconfNodeAugmentBuilder()
            .setNetconfNode(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setSchemaless(true)
                .setTcpOnly(false)
                .setProtocol(null)
                .setLockDatastore(true)
                .setBackoffMultiplier(Decimal64.valueOf("1.5"))
                .setConcurrentRpcLimit(Uint16.ONE)
                // One reconnection attempt
                .setMaxConnectionAttempts(Uint32.ONE)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setMinBackoffMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setConnectionTimeoutMillis(Uint32.valueOf(1000))
                .setMaxBackoffMillis(Uint32.valueOf(1000))
                .setBackoffJitter(Decimal64.valueOf("0.0"))
                .setCredentials(new LoginPwBuilder()
                        .setLoginPassword(new LoginPasswordBuilder()
                                .setUsername("testuser")
                                .setPassword("bGlnaHQgd29y".getBytes(StandardCharsets.UTF_8)).build())
                        .build())
                .build())
            .build();

        final var testNode = new NodeBuilder()
            .setNodeId(new NodeId("nodeId"))
            .addAugmentation(netconfNode)
            .build();

        doNothing().when(delegate).onDeviceFailed(exceptionCaptor.capture());
        doNothing().when(delegate).close();
        doThrow(new GeneralSecurityException()).when(encryptionService).decrypt(any());

        doReturn(testNode).when(dataObjectWritten).dataAfter();
        topology.onDataTreeChanged(List.of(
            new CustomTreeModification(LogicalDatastoreType.OPERATIONAL, path, dataObjectWritten)));
        verify(encryptionService).decrypt(any());
        verify(delegate).onDeviceFailed(any(IllegalStateException.class));

        assertEquals("Failed to decrypt password",
            exceptionCaptor.getValue().getMessage());

        final var keyStep = new KeyStep<>(Node.class, new NodeKey(testNode.getNodeId()));
        doReturn(keyStep).when(dataObjectDeleted).coerceKeyStep(Node.class);
        topology.onDataTreeChanged(List.of(
            new CustomTreeModification(LogicalDatastoreType.OPERATIONAL, path, dataObjectDeleted)));
        verify(delegate).close();
    }

    @Test
    void callHomeNodeIsRejectedWhileConfigured() throws Exception {
        final var topology = newTopology();
        final var connectFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(connectFuture).when(clientFactory).createClient(any(), any());
        topology.ensureNode(configuredNode());

        topology.lockedEnsureCallHomeNode(configuredNode(), null, initializer);

        // the call-home node is not created and the configured node is left intact
        verifyNoInteractions(initializer);
        assertFalse(connectFuture.isCancelled());
        verify(delegate, never()).close();
    }

    @Test
    void configuredNodeIsIgnoredWhileCallHomeConnected() throws Exception {
        final var topology = newTopology();
        final var sessionFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(sessionFuture).when(initializer).initialize(any());
        topology.lockedEnsureCallHomeNode(configuredNode(), null, initializer);
        verify(initializer).initialize(any());

        topology.ensureNode(configuredNode());

        // no outbound connection is attempted and the call-home node is left intact
        verifyNoInteractions(clientFactory);
        assertFalse(sessionFuture.isCancelled());
        verify(delegate, never()).close();
    }

    @Test
    void callHomeNodeReplacesPreviousCallHomeNode() throws Exception {
        final var topology = newTopology();
        final var sessionFuture = SettableFuture.<NetconfClientSession>create();
        final var otherSessionFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(sessionFuture).when(initializer).initialize(any());
        doReturn(otherSessionFuture).when(otherInitializer).initialize(any());
        topology.lockedEnsureCallHomeNode(configuredNode(), null, initializer);
        doNothing().when(delegate).close();

        topology.lockedEnsureCallHomeNode(configuredNode(), null, otherInitializer);

        // the previous call-home node is disconnected and replaced
        assertTrue(sessionFuture.isCancelled());
        verify(delegate).close();
        verify(otherInitializer).initialize(any());
        assertFalse(otherSessionFuture.isCancelled());
    }

    @Test
    void deleteNodeDoesNotDisconnectCallHomeNode() throws Exception {
        final var topology = newTopology();
        final var sessionFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(sessionFuture).when(initializer).initialize(any());
        topology.lockedEnsureCallHomeNode(configuredNode(), null, initializer);

        topology.deleteNode(NODE_ID);
        assertFalse(sessionFuture.isCancelled());
        verify(delegate, never()).close();

        doNothing().when(delegate).close();
        topology.deleteCallHomeNode(NODE_ID);
        assertTrue(sessionFuture.isCancelled());
        verify(delegate).close();
    }

    @Test
    void deleteCallHomeNodeDoesNotDisconnectConfiguredNode() throws Exception {
        final var topology = newTopology();
        final var connectFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(connectFuture).when(clientFactory).createClient(any(), any());
        topology.ensureNode(configuredNode());

        topology.deleteCallHomeNode(NODE_ID);
        assertFalse(connectFuture.isCancelled());
        verify(delegate, never()).close();

        doNothing().when(delegate).close();
        topology.deleteNode(NODE_ID);
        assertTrue(connectFuture.isCancelled());
        verify(delegate).close();
    }

    @Test
    void sshParamsUpdateDoesNotReconnectCallHomeNode() throws Exception {
        final var topology = newTopology();
        final var connectFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(connectFuture).when(clientFactory).createClient(any(), any());
        topology.ensureNode(configuredNode());

        final var sessionFuture = SettableFuture.<NetconfClientSession>create();
        doReturn(sessionFuture).when(initializer).initialize(any());
        topology.lockedEnsureCallHomeNode(configuredNode(new NodeId("callHomeNodeId")), null, initializer);

        doNothing().when(wtx).put(any(), any(DataObjectIdentifier.class), any());
        doNothing().when(delegate).close();
        topology.updateSshParams(AbstractNetconfTopology.defaultSshParams());

        // the configured node is reconnected ...
        assertTrue(connectFuture.isCancelled());
        verify(clientFactory, times(2)).createClient(any(), any());
        // ... while the call-home node is left intact
        assertFalse(sessionFuture.isCancelled());
        verify(initializer).initialize(any());
        verify(delegate).close();
    }

    private TestingNetconfTopologyImpl newTopology() {
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();
        doNothing().when(wtx).merge(any(), any(DataObjectIdentifier.class), any());

        return new TestingNetconfTopologyImpl("id", clientFactory, timer, new NetconfTopologySchemaAssembler(1),
            schemaManager, dataBroker, mountPointService,
            new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
                sslContextFactoryProvider), deviceActionFactory,
            new DefaultBaseNetconfSchemaProvider(DaggerDefaultYangParserComponent.create().parserFactory()));
    }

    private static Node configuredNode() {
        return configuredNode(NODE_ID);
    }

    private static Node configuredNode(final NodeId nodeId) {
        return new NodeBuilder()
            .setNodeId(nodeId)
            .addAugmentation(new NetconfNodeAugmentBuilder()
                .setNetconfNode(new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                    .setPort(new PortNumber(Uint16.valueOf(9999)))
                    .setReconnectOnChangedSchema(true)
                    .setSchemaless(true)
                    .setTcpOnly(true)
                    .setLockDatastore(true)
                    .setBackoffMultiplier(Decimal64.valueOf("1.5"))
                    .setConcurrentRpcLimit(Uint16.ONE)
                    .setMaxConnectionAttempts(Uint32.ONE)
                    .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                    .setMinBackoffMillis(Uint16.valueOf(100))
                    .setKeepaliveDelay(Uint32.ZERO)
                    .setConnectionTimeoutMillis(Uint32.valueOf(1000))
                    .setMaxBackoffMillis(Uint32.valueOf(1000))
                    .setBackoffJitter(Decimal64.valueOf("0.0"))
                    .build())
                .build())
            .build();
    }

    private final class TestingNetconfTopologyImpl extends AbstractNetconfTopology
            implements DataTreeChangeListener<Node> {
        TestingNetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaManager, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory,final BaseNetconfSchemaProvider baseSchemaProvider) {
            super(topologyId, clientFactory, timer, schemaAssembler, schemaManager, dataBroker, mountPointService,
                    builderFactory, deviceActionFactory, baseSchemaProvider);
        }

        // Want to simulate on data tree change
        @Override
        public void onDataTreeChanged(final List<DataTreeModification<Node>> changes) {
            for (var change : changes) {
                switch (change.getRootNode()) {
                    case DataObjectWritten<Node> written -> ensureNode(written.dataAfter());
                    case DataObjectDeleted<Node> deleted ->
                        deleteNode(deleted.coerceKeyStep(Node.class).key().getNodeId());
                    case DataObjectModified<Node> modified ->
                        throw new IllegalArgumentException("Unexpected modification type: " + modified);
                }
            }
        }

        @Override
        protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, final Credentials credentials,
                final boolean lockDatastore) {
            return delegate;
        }
    }
}
