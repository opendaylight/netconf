/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import java.net.InetSocketAddress;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;

/**
 * Base class for TCP-based {@link TransportStack}s.
 */
public abstract sealed class TCPTransportStack extends AbstractTransportStack<TCPTransportChannel>
        permits TCPClient, TCPServer {
    TCPTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

    static final InetSocketAddress socketAddressOf(final Host host, final PortNumber port) {
        final var addr = host.getIpAddress();
        return addr != null ? socketAddressOf(addr, port)
            : InetSocketAddress.createUnresolved(host.getDomainName().getValue(), port.getValue().toJava());
    }

    static final InetSocketAddress socketAddressOf(final IpAddress addr, final PortNumber port) {
        final int portNum = port == null ? 0 : port.getValue().toJava();
        if (addr == null) {
            return port == null ? null : new InetSocketAddress(portNum);
        }
        return new InetSocketAddress(IetfInetUtil.inetAddressFor(addr), portNum);
    }
}
