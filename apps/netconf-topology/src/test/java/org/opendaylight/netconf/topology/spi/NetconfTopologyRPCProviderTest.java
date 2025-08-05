/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.RpcCredentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.binding.Rpc;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class NetconfTopologyRPCProviderTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";
    private static final String TEST_PWD =  "test";
    private static final String ENC_PWD = "4o9/Hn3Pi4150YrP12N/1g==";

    @Mock
    private RpcProviderService rpcProviderService;
    @Mock
    private Registration rpcReg;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private AAAEncryptionService encryptionService;

    private NetconfTopologyRPCProvider rpcProvider;

    @BeforeEach
    void setUp() {
        doReturn(rpcReg).when(rpcProviderService).registerRpcImplementations(any(Rpc[].class));
        rpcProvider = new NetconfTopologyRPCProvider(rpcProviderService, dataBroker, encryptionService, TOPOLOGY_ID);
    }

    @Test
    void testEncryptPassword() throws Exception {
        doReturn(ENC_PWD.getBytes()).when(encryptionService).encrypt(TEST_PWD.getBytes());
        final var encryptedPwNode = rpcProvider.encryptPassword(getInput(true)).getNetconfNode();
        final var loginPw = assertInstanceOf(LoginPw.class, encryptedPwNode.getCredentials());

        assertArrayEquals(ENC_PWD.getBytes(), loginPw.getLoginPassword().getPassword());
    }

    @Test
    void testNoEncryption() {
        final NetconfNode encryptedPwNode = rpcProvider.encryptPassword(getInput(false)).getNetconfNode();
        final var loginPw = assertInstanceOf(LoginPwUnencrypted.class, encryptedPwNode.getCredentials());

        assertEquals(TEST_PWD, loginPw.getLoginPasswordUnencrypted().getPassword());
    }

    private static CreateDeviceInput getInput(final boolean encrypt) {
        final RpcCredentials credentials;
        if (encrypt) {
            credentials = new LoginPwBuilder()
                .setLoginPassword(new LoginPasswordBuilder().setUsername("test").setPassword(TEST_PWD).build())
                .build();
        } else {
            credentials = new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("test")
                    .setPassword(TEST_PWD)
                    .build())
                .build();
        }

        return new CreateDeviceInputBuilder()
            .setRpcCredentials(credentials)
            .setHost(new Host(new IpAddress(new Ipv4Address("10.18.16.188"))))
            .setPort(new PortNumber(Uint16.valueOf(830)))
            .setTcpOnly(Boolean.FALSE)
            .setNodeId(NODE_ID.getValue())
            .build();
    }
}
