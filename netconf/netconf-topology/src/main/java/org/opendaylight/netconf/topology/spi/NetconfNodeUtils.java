/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import java.net.InetSocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

/**
 * Utility methods to work with {@link NetconfNode} information.
 */
public final class NetconfNodeUtils {
    private NetconfNodeUtils() {
        // Hidden on purpose
    }
    
    public static @NonNull InetSocketAddress toInetSocketAddress(final NetconfNode node) {
        final Host host = node.requireHost();
        final int port = node.requirePort().getValue().toJava();
        final IpAddress ipAddress = host.getIpAddress();
        return ipAddress != null ? new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(ipAddress), port)
            : new InetSocketAddress(host.getDomainName().getValue(), port);
    }
    
    public static @NonNull RemoteDeviceId toRemoteDeviceId(final NodeId nodeId, final NetconfNode node) {
        return new RemoteDeviceId(nodeId.getValue(), toInetSocketAddress(node));
    }
}
