/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.sal.connect.util.NetconfTopologyRPCProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.CreateDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

public class NetconfTopologyRPCProviderTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final String TOPOLOGY_ID = "testing-topology";
    private static final String TEST_PWD =  "test";
    private static final String ENC_PWD = "4o9/Hn3Pi4150YrP12N/1g==";

    @Mock
    private DataBroker dataBroker;

    @Mock
    private AAAEncryptionService encryptionService;

    private NetconfTopologyRPCProvider rpcProvider ;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(encryptionService.encrypt(TEST_PWD)).thenReturn(ENC_PWD);
        rpcProvider = new NetconfTopologyRPCProvider(dataBroker, encryptionService, TOPOLOGY_ID);
    }

    @Test
    public void testEncryptPassword() throws Exception {

        final NetconfNode encryptedPwNode = rpcProvider.encryptPassword(getInput(true));

        final Credentials credentials = encryptedPwNode.getCredentials();
        assertTrue(credentials instanceof LoginPw);
        final LoginPw loginPw = (LoginPw) credentials;

        assertEquals(ENC_PWD, loginPw.getLoginPassword().getPassword());
    }

    @Test
    public void testNoEncryption() throws Exception {
        final NetconfNode encryptedPwNode = rpcProvider.encryptPassword(getInput(false));

        final Credentials credentials = encryptedPwNode.getCredentials();
        assertTrue(credentials instanceof LoginPwUnencrypted);
        final LoginPwUnencrypted loginPw = (LoginPwUnencrypted) credentials;

        assertEquals(TEST_PWD, loginPw.getLoginPasswordUnencrypted().getPassword());
    }

    private static CreateDeviceInput getInput(final boolean encrypt) {
        CreateDeviceInputBuilder builder = new CreateDeviceInputBuilder();
        final Credentials credentials;
        if (encrypt) {
            credentials = new LoginPwBuilder().setLoginPassword(
                    new LoginPasswordBuilder().setUsername("test").setPassword(TEST_PWD).build()).build();
        } else {
            credentials = new LoginPwUnencryptedBuilder().setLoginPasswordUnencrypted(
                    new LoginPasswordUnencryptedBuilder().setUsername("test").setPassword(TEST_PWD).build()).build();
        }

        builder.setCredentials(credentials);
        builder.setHost(new Host(new IpAddress(new Ipv4Address("10.18.16.188"))));
        builder.setPort(new PortNumber(830));
        builder.setTcpOnly(Boolean.FALSE);
        builder.setNodeId(NODE_ID.toString());
        return builder.build();
    }

}
