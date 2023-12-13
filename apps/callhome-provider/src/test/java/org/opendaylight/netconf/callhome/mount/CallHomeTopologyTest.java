/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev231025.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev231025.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@ExtendWith(MockitoExtension.class)
public class CallHomeTopologyTest {
    private static final Protocol SSH_PROTOCOL = new ProtocolBuilder().setName(Protocol.Name.SSH).build();

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
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private NetconfClientConfigurationBuilderFactory builderFactory;
    @Mock
    private WriteTransaction wtx;
    @Mock
    private TransactionChain txChain;
    @Mock
    private WriteTransaction tx;
    @Mock
    private NetconfClientConfigurationBuilder netconfClientConfigurationBuilder;
    @Mock
    private NetconfClientConfigurationBuilder netconfClientConfigurationBuilder2;
    @Mock
    private ListenableFuture<NetconfClientSession> session;

    private CallHomeTopology topology;

    @Test
    void createDeviceIntoNetconfTopologyTest() throws Exception {
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();
        doReturn(txChain).when(dataBroker).createMergingTransactionChain(any());
        doReturn(tx).when(txChain).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        topology = new CallHomeTopology("topology_id", mockedClientFactory, mockedTimer,
            MoreExecutors.directExecutor(), mockedResourceManager, dataBroker, mountPointService, builderFactory,
            new DefaultBaseNetconfSchemas(new DefaultYangParserFactory()), deviceActionFactory);

        final var node = new NodeBuilder()
            .setNodeId(new NodeId("id1"))
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(IetfInetUtil.ipAddressFor("127.0.0.1")))
                .setPort(new PortNumber(Uint16.valueOf(12345)))
                .setTcpOnly(false)
                .setProtocol(SSH_PROTOCOL)
                // below parameters are required for NetconfNodeHandler
                .setSchemaless(true)
                .setReconnectOnChangedSchema(false)
                .setConnectionTimeoutMillis(Uint32.valueOf(20000))
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(60000))
                .setMaxConnectionAttempts(Uint32.ZERO)
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(2000))
                .setSleepFactor(Decimal64.valueOf("1.5"))
                .setKeepaliveDelay(Uint32.valueOf(120))
                .setConcurrentRpcLimit(Uint16.ZERO)
                .setActorResponseWaitTime(Uint16.valueOf(5))
                .setLockDatastore(true)
                .build())
            .build();

        doReturn(netconfClientConfigurationBuilder).when(builderFactory)
            .createClientConfigurationBuilder(node.getNodeId(), node.augmentation(NetconfNode.class));
        doReturn(netconfClientConfigurationBuilder2).when(netconfClientConfigurationBuilder)
                .withSessionListener(any());
        doReturn(session).when(mockedClientFactory).createClient(any());


        topology.enableNode(node);
        verify(session).addListener(any(), any());
    }
}