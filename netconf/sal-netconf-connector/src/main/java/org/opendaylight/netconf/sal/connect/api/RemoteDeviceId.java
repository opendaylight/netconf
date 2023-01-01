/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import static java.util.Objects.requireNonNull;

import java.net.InetSocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.network.topology.topology.topology.types.TopologyNetconf;

public record RemoteDeviceId(@NonNull String name, @NonNull InetSocketAddress address) {
    // FIXME: extract all of this to users, as they are in control of topology-id
    @Deprecated(since = "5.0.0", forRemoval = true)
    public static final String DEFAULT_TOPOLOGY_NAME = TopologyNetconf.QNAME.getLocalName();

    public RemoteDeviceId {
        requireNonNull(name);
        requireNonNull(address);
    }

    public @NonNull Host host() {
        final var addr = address.getAddress();
        return addr != null ? new Host(IetfInetUtil.INSTANCE.ipAddressFor(addr))
            : new Host(new DomainName(address.getHostString()));
    }
}
