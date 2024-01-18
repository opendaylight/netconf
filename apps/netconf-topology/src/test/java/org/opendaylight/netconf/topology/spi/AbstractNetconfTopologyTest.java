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

import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

class AbstractNetconfTopologyTest {
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
}
