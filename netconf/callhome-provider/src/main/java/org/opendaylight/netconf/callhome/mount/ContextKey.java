/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNode;

class ContextKey {

    private final IpAddress address;
    private final PortNumber port;

    ContextKey(IpAddress address, PortNumber port) {
        this.address = Preconditions.checkNotNull(address);
        this.port = Preconditions.checkNotNull(port);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + address.hashCode();
        result = prime * result + port.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContextKey other = (ContextKey) obj;
        return Objects.equal(address, other.address) && Objects.equal(port, other.port);
    }

    IpAddress getIpAddress() {
        return address;
    }

    PortNumber getPort() {
        return port;
    }

    public static ContextKey from(NetconfNode node) {
        return new ContextKey(node.getHost().getIpAddress(), node.getPort());
    }

    public static ContextKey from(SocketAddress remoteAddress) {
        Preconditions.checkArgument(remoteAddress instanceof InetSocketAddress);
        InetSocketAddress inetSocketAddr = (InetSocketAddress) remoteAddress;
        InetAddress ipAddress = inetSocketAddr.getAddress();

        final IpAddress yangIp;
        if (ipAddress instanceof Inet4Address) {
            yangIp = new IpAddress(IetfInetUtil.INSTANCE.ipv4AddressFor(ipAddress));
        } else {
            Preconditions.checkArgument(ipAddress instanceof Inet6Address);
            yangIp = new IpAddress(IetfInetUtil.INSTANCE.ipv6AddressFor(ipAddress));
        }
        return new ContextKey(yangIp, new PortNumber(inetSocketAddr.getPort()));
    }

    @Override
    public String toString() {
        if (address.getIpv4Address() != null) {
            return address.getIpv4Address().getValue() + ":" + port.getValue();
        }
        return address.getIpv6Address().getValue() + ":" + port.getValue();
    }
}
