/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@ExtendWith(MockitoExtension.class)
class NetconfTopologyImplTest {
    private static final TopologyKey TOPOLOGY_KEY = new TopologyKey(new TopologyId("testing-topology"));
    private static final DataObjectIdentifier.WithKey<Topology, TopologyKey> TOPOLOGY_PATH =
        DataObjectIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY_KEY).build();

    @Mock
    private NetconfClientFactory mockedClientFactory;
    @Mock
    private NetconfTimer mockedTimer;
    @Mock
    private SchemaResourceManager mockedResourceManager;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private RpcProviderService rpcProviderService;
    @Mock
    private NetconfClientConfigurationBuilderFactory builderFactory;
    @Mock
    private WriteTransaction wtx;
    @Mock
    private DataObjectDeleted<Node> objDeleted;
    @Mock
    private DataObjectWritten<Node> objWritten;
    @Mock
    private DataObjectModified<Node> objModified;
    @Mock
    private DataTreeModification<Node> treeMod;

    @Test
    void testOnDataTreeChange() throws Exception {
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();

        try (var schemaAssembler = new NetconfTopologySchemaAssembler(1)) {
            final var topology = new TestingNetconfTopologyImpl(TOPOLOGY_KEY.getTopologyId().getValue(),
                mockedClientFactory, mockedTimer, schemaAssembler, mockedResourceManager, dataBroker, mountPointService,
                encryptionService, builderFactory, rpcProviderService,
                new DefaultBaseNetconfSchemaProvider(new DefaultYangParserFactory()));
            //verify initialization of topology
            verify(wtx).merge(LogicalDatastoreType.OPERATIONAL, TOPOLOGY_PATH,
                new TopologyBuilder().withKey(TOPOLOGY_KEY).build());

            final var spyTopology = spy(topology);

            final var key = new NodeKey(new NodeId("testing-node"));
            final var node = new NodeBuilder()
                .withKey(key)
                .addAugmentation(new NetconfNodeAugmentBuilder()
                    .setNetconfNode(new NetconfNodeBuilder()
                        .setLockDatastore(true)
                        .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                        .setPort(new PortNumber(Uint16.valueOf(9999)))
                        .setReconnectOnChangedSchema(true)
                        .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                        .setMinBackoffMillis(Uint16.valueOf(100))
                        .setKeepaliveDelay(Uint32.valueOf(1000))
                        .setTcpOnly(true)
                        .setCredentials(new LoginPwUnencryptedBuilder()
                            .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                                .setUsername("testuser")
                                .setPassword("testpassword")
                                .build())
                            .build())
                        .build())
                    .build())
                .build();

            doReturn(ModificationType.WRITE).when(objWritten).modificationType();
            doReturn(node).when(objWritten).dataAfter();

            final var changes = List.of(treeMod);

            doReturn(objWritten).when(treeMod).getRootNode();
            spyTopology.onDataTreeChanged(changes);
            verify(spyTopology).ensureNode(node);

            doReturn(TOPOLOGY_PATH.toBuilder().child(Node.class, key).build()).when(treeMod).path();
            doReturn(ModificationType.DELETE).when(objDeleted).modificationType();
            doReturn(objDeleted).when(treeMod).getRootNode();
            spyTopology.onDataTreeChanged(changes);
            verify(spyTopology).deleteNode(key.getNodeId());

            doReturn(ModificationType.SUBTREE_MODIFIED).when(objModified).modificationType();
            doReturn(node).when(objModified).dataAfter();
            doReturn(objModified).when(treeMod).getRootNode();
            spyTopology.onDataTreeChanged(changes);

            // one in previous creating and deleting node and one in updating
            verify(spyTopology, times(2)).ensureNode(node);
        }
    }

    private static class TestingNetconfTopologyImpl extends NetconfTopologyImpl {
        TestingNetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientFactory,
                final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
                final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
                final DOMMountPointService mountPointService, final AAAEncryptionService encryptionService,
                final NetconfClientConfigurationBuilderFactory builderFactory,
                final RpcProviderService rpcProviderService, final BaseNetconfSchemaProvider baseSchemaProvider) {
            super(topologyId, clientFactory, timer, schemaAssembler, schemaRepositoryProvider, dataBroker,
                mountPointService, encryptionService, builderFactory, rpcProviderService, baseSchemaProvider);
        }

        @Override
        public void ensureNode(final Node configNode) {
            // No-op
        }

        @Override
        public void deleteNode(final NodeId nodeId) {
            // No-op
        }
    }
}
