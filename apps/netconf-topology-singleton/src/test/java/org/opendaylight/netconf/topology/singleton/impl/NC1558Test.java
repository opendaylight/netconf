/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;
import static org.opendaylight.netconf.topology.singleton.impl.AbstractBaseSchemasTest.BASE_SCHEMAS;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.FutureCallback;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.Optional;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251028.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251028.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NC1558Test {
    private static final String TOPOLOGY_ID = TopologyNetconf.QNAME.getLocalName();
    private static final DataObjectIdentifier.WithKey<Node, NodeKey> NODE_INSTANCE_ID =
        NetconfTopologyUtils.createTopologyNodeListPath(new NodeKey(new NodeId("node-id")), TOPOLOGY_ID);
    private static final Host HOST = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
    private static final Host UPDATED_HOST = new Host(new IpAddress(new Ipv4Address("127.0.0.2")));
    private static final PortNumber PORT = new PortNumber(Uint16.valueOf(1234));
    private static final PortNumber UPDATED_PORT = new PortNumber(Uint16.valueOf(9876));
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private NetconfClientConfigurationBuilderFactory builderFactory;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private ServiceGroupIdentifier serviceGroupIdent;
    @Mock
    private NetconfTimer mockTimer;
    @Mock
    private ClusterSingletonServiceProvider mockSlaveClusterSingletonServiceProvider;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private NetconfClientFactory mockClientFactory;
    @Mock
    private DeviceNetconfSchemaProvider deviceSchemaProvider;
    @Mock
    private TransactionChain mockChain;
    @Mock
    private WriteTransaction tx;
    @Captor
    private ArgumentCaptor<FutureCallback<Empty>> callback;

    private ActorSystem actorSystem;

    @Test
    void test() throws Exception {
        doReturn(mockChain).when(dataBroker).createMergingTransactionChain();
        doReturn(tx).when(mockChain).newWriteOnlyTransaction();
        doReturn(emptyFluentFuture()).when(tx).commit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockClientFactory).createClient(any());
        actorSystem = ActorSystem.create("test", ConfigFactory.load().getConfig("Master"));

        final var netconfNode = createNetconfNode(HOST, PORT);
        final var node  = createNode(netconfNode);
        final var setup = createSetup(node);

        final var context = new NetconfTopologyContext(schemaManager, mountPointService, builderFactory,
            deviceActionFactory, Timeout.create(Duration.ofSeconds(5)), serviceGroupIdent, setup);

        doReturn(NetconfClientConfigurationBuilder.create()
                .withName("node-id")
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                .withTcpParameters(new TcpClientParametersBuilder().build()))
            .when(builderFactory)
            .createClientConfigurationBuilder(new NodeId("node-id"), netconfNode,
                AbstractNetconfTopology.defaultSshParams());

        context.instantiateServiceInstance();
        verify(mockChain).addCallback(callback.capture());
        doAnswer(ignored -> {
            callback.getValue().onSuccess(null);
            return null;
        }).when(mockChain).close();

        final var netconfNodeUpdated = createNetconfNode(UPDATED_HOST, UPDATED_PORT);
        final var nodeUpdated  = createNode(netconfNodeUpdated);
        final var setupUpdated = createSetup(nodeUpdated);
        context.refresh(setupUpdated);

        verify(builderFactory, timeout(2000)).createClientConfigurationBuilder(new NodeId("node-id"),
            netconfNodeUpdated, AbstractNetconfTopology.defaultSshParams());

        TestKit.shutdownActorSystem(actorSystem, true);
    }

    private static Node createNode(final NetconfNode netconfNode) {
        return new NodeBuilder()
            .withKey(NODE_INSTANCE_ID.key())
            .addAugmentation(new NetconfNodeAugmentBuilder()
                .setNetconfNode(netconfNode)
                .build())
            .build();
    }

    private NetconfTopologySetup createSetup(final Node node) {
        return NetconfTopologySetup.builder()
            .setClusterSingletonServiceProvider(mockSlaveClusterSingletonServiceProvider)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .setDataBroker(dataBroker)
            .setInstanceIdentifier(NODE_INSTANCE_ID)
            .setNode(node)
            .setActorSystem(actorSystem)
            .setTimer(mockTimer)
            .setSchemaAssembler(new NetconfTopologySchemaAssembler(1))
            .setTopologyId(TOPOLOGY_ID)
            .setNetconfClientFactory(mockClientFactory)
            .setDeviceSchemaProvider(deviceSchemaProvider)
            .setIdleTimeout(Duration.ofSeconds(10))
            .setSshParams(AbstractNetconfTopology.defaultSshParams())
            .build();
    }

    private static NetconfNode createNetconfNode(final Host host, final PortNumber port) {
        return new NetconfNodeBuilder()
            .setHost(host)
            .setPort(port)
            .setActorResponseWaitTime(Uint16.valueOf(10))
            .setTcpOnly(Boolean.TRUE)
            .setSchemaless(Boolean.FALSE)
            .setKeepaliveDelay(Uint32.ZERO)
            .setConcurrentRpcLimit(Uint16.ONE)
            .setConnectionTimeoutMillis(Uint32.valueOf(5000))
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(5000))
            .setMaxConnectionAttempts(Uint32.ONE)
            .setLockDatastore(true)
            .setSchemaless(true)
            .setMinBackoffMillis(Uint16.valueOf(1000))
            .setMaxBackoffMillis(Uint32.valueOf(10000))
            .setBackoffMultiplier(Decimal64.valueOf("1.5"))
            .setBackoffJitter(Decimal64.valueOf("0.0"))
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("user")
                    .setPassword("pass")
                    .build())
                .build())
            .setSchemaCacheDirectory("test-schema")
            .build();
    }
}
