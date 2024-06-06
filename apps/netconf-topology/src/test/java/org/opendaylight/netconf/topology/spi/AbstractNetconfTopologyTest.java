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
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;

import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@ExtendWith(MockitoExtension.class)
class AbstractNetconfTopologyTest {
    @Mock
    private NetconfClientDispatcher clientDispatcher;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private EventExecutor eventExecutor;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private WriteTransaction wtx;
    @Mock
    private RemoteDeviceHandler delegate;
    @Mock
    private ScheduledThreadPool keepaliveExecutor;
    @Mock
    private ThreadPool processingExecutor;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private DefaultNetconfClientConfigurationBuilderFactory factory;

    @Captor
    private ArgumentCaptor<Throwable> exceptionCaptor;

    private TestingNetconfTopologyImpl topology;

    @Test
    void hideCredentials() {
        final String userName = "admin";
        final String password = "pa$$word";
        final Node node = new NodeBuilder()
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                    .setPort(new PortNumber(Uint16.valueOf(9999)))
                    .setReconnectOnChangedSchema(true)
                    .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                    .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                    .setKeepaliveDelay(Uint32.valueOf(1000))
                    .setTcpOnly(false)
                    .setProtocol(new ProtocolBuilder().setName(Name.TLS).build())
                    .setCredentials(new LoginPasswordBuilder()
                        .setUsername(userName)
                        .setPassword(password)
                        .build())
                    .build())
                .setNodeId(NodeId.getDefaultInstance("junos"))
                .build();
        final String transformedNetconfNode = AbstractNetconfTopology.hideCredentials(node);
        assertTrue(transformedNetconfNode.contains("credentials=***"));
        assertFalse(transformedNetconfNode.contains(userName));
        assertFalse(transformedNetconfNode.contains(password));
    }

    @Test
    void hideNullCredentials() {
        final Node node = new NodeBuilder()
            .setNodeId(new NodeId("id"))
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setSchemaless(false)
                .setReconnectOnChangedSchema(false)
                .setMaxConnectionAttempts(Uint32.ZERO)
                .setLockDatastore(true)
                .build())
            .build();
        assertNotNull(AbstractNetconfTopology.hideCredentials(node));
    }

    @Test
    void testFailToDecryptPassword() throws Exception {
        doReturn(MoreExecutors.newDirectExecutorService()).when(processingExecutor).getExecutor();
        doReturn(Executors.newScheduledThreadPool(1)).when(keepaliveExecutor).getExecutor();
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();
        doNothing().when(wtx).merge(any(), any(), any());

        topology = new TestingNetconfTopologyImpl("id", clientDispatcher, eventExecutor, keepaliveExecutor,
            processingExecutor, schemaManager, dataBroker, mountPointService, factory, deviceActionFactory,
            new DefaultBaseNetconfSchemas(new DefaultYangParserFactory()));

        final var netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setSchemaless(true)
                .setTcpOnly(false)
                .setProtocol(null)
                .setLockDatastore(true)
                .setConcurrentRpcLimit(Uint16.ONE)
                // One reconnection attempt
                .setMaxConnectionAttempts(Uint32.ONE)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setSleepFactor(Decimal64.valueOf("0.0"))
                .setConnectionTimeoutMillis(Uint32.valueOf(1000))
                .setMaxTimeoutBetweenAttemptsMillis(Uint32.valueOf(1000))
                .setBackoffJitter(Decimal64.valueOf("0.0"))
                .setCredentials(new LoginPasswordBuilder()
                    .setUsername("testuser")
                    .setPassword("testpassword")
                    .build())
                .build();

        final var testNode = new NodeBuilder()
            .setNodeId(new NodeId("nodeId"))
            .addAugmentation(netconfNode)
            .build();

        doNothing().when(delegate).onDeviceFailed(exceptionCaptor.capture());
        doNothing().when(delegate).close();
        doThrow(new IllegalStateException()).when(factory).createClientConfigurationBuilder(testNode.getNodeId(),
            netconfNode);

        topology.onDataTreeChanged(testNode, ModificationType.WRITE);
        verify(delegate).onDeviceFailed(any(ConnectGivenUpException.class));

        assertEquals("Given up connecting RemoteDeviceId[name=nodeId, address=/127.0.0.1:9999] after 1 attempts",
            exceptionCaptor.getValue().getMessage());

        topology.onDataTreeChanged(testNode, ModificationType.DELETE);
        verify(delegate).close();
    }

    private class TestingNetconfTopologyImpl extends AbstractNetconfTopology {

        protected TestingNetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
                final DataBroker dataBroker, final DOMMountPointService mountPointService,
                final NetconfClientConfigurationBuilderFactory builderFactory,
                final DeviceActionFactory deviceActionFactory,final BaseNetconfSchemas baseSchemas) {
            super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor, schemaManager,
                dataBroker, mountPointService, builderFactory, deviceActionFactory, baseSchemas);
        }

        // Want to simulate on data tree change
        public void onDataTreeChanged(final Node node, final ModificationType type) {
            switch (type) {
                case WRITE -> ensureNode(node);
                case DELETE -> deleteNode(node.getNodeId());
                default -> throw new IllegalArgumentException("Unexpected modification type: " + type);
            }
        }

        @Override
        protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, boolean lockDatastore) {
            return delegate;
        }
    }
}
