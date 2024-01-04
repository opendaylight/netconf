/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.NoSuchElementException;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240104.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

public class NetconfNodeUtilsTest {
    @Test
    public void testCreateRemoteDeviceId() {
        final var host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        final var id = NetconfNodeUtils.toRemoteDeviceId(new NodeId("testing-node"), new NetconfNodeBuilder()
            .setHost(host)
            .setPort(new PortNumber(Uint16.valueOf(9999)))
            .build());

        assertEquals("testing-node", id.name());
        assertEquals(host, id.host());
        assertEquals(9999, id.address().getPort());
    }

    @Test
    public void toInetSocketAddressRequiresHostPort() {
        // Creates a netconfNode without specifying a Port and Host.
        final var builder = new NetconfNodeBuilder()
            .setReconnectOnChangedSchema(true)
            .setSchemaless(true)
            .setTcpOnly(true)
            .setSleepFactor(Decimal64.valueOf("1.5"))
            .setConcurrentRpcLimit(Uint16.ONE)
            // One reconnection attempt
            .setMaxConnectionAttempts(Uint32.TWO)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setConnectionTimeoutMillis(Uint32.valueOf(1000))
            .setCredentials(new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername("testuser")
                    .setPassword("testpassword")
                    .build())
                .build());

        assertEquals("Value of host is not present", assertThrows(NoSuchElementException.class,
            () -> NetconfNodeUtils.toInetSocketAddress(builder.build())).getMessage());

        builder.setHost(new Host(new DomainName("testhost")));
        assertEquals("Value of port is not present", assertThrows(NoSuchElementException.class,
            () -> NetconfNodeUtils.toInetSocketAddress(builder.build())).getMessage());
    }
}
