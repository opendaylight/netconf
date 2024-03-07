/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
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
    private Node testNode;
    @Mock
    private WriteTransaction wtx;
    @Mock
    private RemoteDeviceHandler delegate;

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
        doReturn(wtx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wtx).commit();
        doNothing().when(wtx).merge(any(), any(), any());

        final var schemaAssembler = new NetconfTopologySchemaAssembler(1, 1, 0, TimeUnit.SECONDS);
        topology = new TestingNetconfTopologyImpl("id", clientFactory, timer, schemaAssembler,
                schemaManager, dataBroker, mountPointService,
                new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
                        sslContextFactoryProvider), deviceActionFactory,
                new DefaultBaseNetconfSchemaProvider(new DefaultYangParserFactory()));

        final var netconfNode = new NetconfNodeBuilder()
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
                .build();

        doReturn(netconfNode).when(testNode).augmentation(NetconfNode.class);
        doReturn(null).when(testNode).augmentation(NetconfNodeAugmentedOptional.class);
        doReturn(new NodeId("testing-node")).when(testNode).requireNodeId();
        doReturn("").when(testNode).toString();
        doNothing().when(delegate).close();
        // throw exception when try to decrypt
        doThrow(new GeneralSecurityException()).when(encryptionService).decrypt(any());

        topology.onDataTreeChanged(testNode);
        verify(delegate).close();
        verify(encryptionService).decrypt(any());
    }

    private class TestingNetconfTopologyImpl extends AbstractNetconfTopology {

        protected TestingNetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaManager, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory,final BaseNetconfSchemaProvider baseSchemaProvider) {
            super(topologyId, clientFactory, timer, schemaAssembler, schemaManager, dataBroker, mountPointService,
                    builderFactory, deviceActionFactory, baseSchemaProvider);
        }

        // Want to simulate on data tree change
        public void onDataTreeChanged(final Node node) {
            ensureNode(node);
        }

        @Override
        protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, final boolean lockDatastore) {
            return delegate;
        }
    }
}
