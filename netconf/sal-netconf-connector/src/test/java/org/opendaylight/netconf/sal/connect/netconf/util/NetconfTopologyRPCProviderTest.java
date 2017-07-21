/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.AddNetconfNodeInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.AddNetconfNodeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
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

    NetconfTopologyRPCProvider rpcProvider ;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(encryptionService.encrypt(TEST_PWD)).thenReturn(ENC_PWD);
        rpcProvider = new NetconfTopologyRPCProvider(dataBroker, encryptionService, TOPOLOGY_ID);
    }

    @Test
    public void testEncryptPassword() throws Exception {

        NetconfNode node = invokeEncryption(true);
        assertNotEquals(TEST_PWD, ((LoginPassword)node.getCredentials()).getPassword());

        node = invokeEncryption(false);
        assertEquals(TEST_PWD, ((LoginPassword)node.getCredentials()).getPassword());
    }

    private NetconfNode invokeEncryption(boolean encrypt) throws Exception {
        Method method = null;

        method = NetconfTopologyRPCProvider.class.getDeclaredMethod("encryptPassword", AddNetconfNodeInput.class);

        method.setAccessible(true);
        NetconfNode node = null;

        node = (NetconfNode)method.invoke(rpcProvider, getInput(encrypt));

        return node;
    }

    private AddNetconfNodeInput getInput(boolean encrypt) {
        AddNetconfNodeInputBuilder builder = new AddNetconfNodeInputBuilder();
        builder.setCredentials(new LoginPasswordBuilder().setPassword(TEST_PWD).setUsername("test").build());
        builder.setHost(new Host(new IpAddress(new Ipv4Address("10.18.16.188"))));
        builder.setPort(new PortNumber(830));
        builder.setTcpOnly(false);
        builder.setNodeId(NODE_ID.toString());
        builder.setEncrypt(encrypt);
        return builder.build();
    }

}
