/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.yang.common.Uint16;

public class ContextKeyTest {
    private IpAddress address1;
    private IpAddress address2;

    private PortNumber port1;
    private PortNumber port2;

    private NetconfNode mockNode;
    private NetconfClientSession mockSession;

    private ContextKey instance1;
    private ContextKey instance2;
    private ContextKey instance3;
    private ContextKey instance4;

    @Before
    public void setup() {
        address1 = IpAddressBuilder.getDefaultInstance("1.2.3.4");
        address2 = IpAddressBuilder.getDefaultInstance("5.6.7.8");

        port1 = new PortNumber(Uint16.valueOf(123));
        port2 = new PortNumber(Uint16.valueOf(456));

        mockNode = mock(NetconfNode.class);
        mockSession = mock(NetconfClientSession.class);

        instance1 = new ContextKey(address1, port1);
        instance2 = new ContextKey(address2, port2);
        instance3 = new ContextKey(address1, port2);
        instance4 = new ContextKey(address2, port1);

        Host mockHost = mock(Host.class);
        when(mockHost.getIpAddress()).thenReturn(address1);
        when(mockNode.getHost()).thenReturn(mockHost);

        when(mockNode.getPort()).thenReturn(port1);
    }

    @Test
    public void hashCodesForDifferentKeysAreDifferent() {
        // expect
        assertNotEquals(instance1.hashCode(), instance2.hashCode());
        assertNotEquals(instance1.hashCode(), 0);
        assertNotEquals(instance2.hashCode(), 0);
    }

    @Test
    public void variousFlavorsOfEqualWork() {
        // expect
        assertTrue(instance1.equals(instance1));
        assertFalse(instance1.equals(null));
        assertFalse(instance1.equals(123456L));
        assertFalse(instance1.equals(instance2));
        assertFalse(instance1.equals(instance3));
        assertFalse(instance1.equals(instance4));
    }

    @Test
    public void newContextCanBeCreatedFromASocketAddress() {
        // given
        Inet4Address someAddressIpv4 = (Inet4Address) InetAddresses.forString("1.2.3.4");
        Inet6Address someAddressIpv6 = (Inet6Address) InetAddresses.forString("::1");
        // and
        ContextKey key1 = ContextKey.from(new InetSocketAddress(someAddressIpv4, 123));
        ContextKey key2 = ContextKey.from(new InetSocketAddress(someAddressIpv6, 123));
        // expect
        assertNotNull(key1);
        assertNotNull(key1.toString());
        assertNotNull(key2);
        assertNotNull(key2.toString());
    }

    @Test
    public void newContextCanBeCreatedFromANetconfNode() {
        // expect
        assertNotNull(ContextKey.from(mockNode));
    }
}
