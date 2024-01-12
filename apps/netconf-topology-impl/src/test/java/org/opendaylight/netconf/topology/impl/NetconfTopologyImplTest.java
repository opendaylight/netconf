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

import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.Timer;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240119.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240119.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@ExtendWith(MockitoExtension.class)
class NetconfTopologyImplTest {
    private static final TopologyKey TOPOLOGY_KEY = new TopologyKey(new TopologyId("testing-topology"));
    private static final KeyedInstanceIdentifier<Topology, TopologyKey> TOPOLOGY_PATH =
        InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY_KEY).build();

    @Mock
    private NetconfClientFactory mockedClientFactory;
    @Mock
    private Timer mockedTimer;
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
    private DataObjectModification<Node> objMod;
    @Mock
    private DataTreeModification<Node> treeMod;

    private TestingNetconfTopologyImpl topology;
    private TestingNetconfTopologyImpl spyTopology;

    @Test
    void testOnDataTreeChange() throws Exception {
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();

        topology = new TestingNetconfTopologyImpl(TOPOLOGY_KEY.getTopologyId().getValue(), mockedClientFactory,
            mockedTimer, MoreExecutors.directExecutor(), mockedResourceManager, dataBroker, mountPointService,
            encryptionService, builderFactory, rpcProviderService,
            new DefaultBaseNetconfSchemas(new DefaultYangParserFactory()));
        //verify initialization of topology
        verify(wtx).merge(LogicalDatastoreType.OPERATIONAL, TOPOLOGY_PATH,
            new TopologyBuilder().withKey(TOPOLOGY_KEY).build());

        spyTopology = spy(topology);

        final var key = new NodeKey(new NodeId("testing-node"));
        final var node = new NodeBuilder()
            .withKey(key)
            .addAugmentation(new NetconfNodeBuilder()
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
            .build();

        doReturn(DataObjectModification.ModificationType.WRITE).when(objMod).getModificationType();
        doReturn(node).when(objMod).getDataAfter();

        doReturn(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, TOPOLOGY_PATH.child(Node.class, key)))
            .when(treeMod).getRootPath();
        final var changes = List.of(treeMod);

        doReturn(objMod).when(treeMod).getRootNode();
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).ensureNode(node);

        doReturn(DataObjectModification.ModificationType.DELETE).when(objMod).getModificationType();
        spyTopology.onDataTreeChanged(changes);
        verify(spyTopology).deleteNode(key.getNodeId());

        doReturn(DataObjectModification.ModificationType.SUBTREE_MODIFIED).when(objMod).getModificationType();
        spyTopology.onDataTreeChanged(changes);

        // one in previous creating and deleting node and one in updating
        verify(spyTopology, times(2)).ensureNode(node);
    }

    private static class TestingNetconfTopologyImpl extends NetconfTopologyImpl {
        TestingNetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientFactory, final Timer timer,
                final Executor processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
                final DataBroker dataBroker, final DOMMountPointService mountPointService,
                final AAAEncryptionService encryptionService,
                final NetconfClientConfigurationBuilderFactory builderFactory,
                final RpcProviderService rpcProviderService, final BaseNetconfSchemas baseSchemas) {
            super(topologyId, clientFactory, timer, processingExecutor, schemaRepositoryProvider,
                dataBroker, mountPointService, encryptionService, builderFactory, rpcProviderService, baseSchemas);
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
