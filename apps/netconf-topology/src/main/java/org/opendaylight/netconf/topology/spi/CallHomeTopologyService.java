/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * A NETCONF topology capable of managing nodes connected through NETCONF call-home (RFC 8071). The call-home server
 * accepts the transport from the device and uses this service to expose the device as a node of the topology, i.e.
 * with a mount point and an operational topology node, without the topology connecting to the device itself.
 *
 * <p>Call-home nodes share the {@link NodeId} space with nodes configured in the topology. Configured nodes take
 * precedence: a call-home node is not created while a configured node with the same identifier exists, and
 * configuration of a node is ignored while a call-home node with the same identifier is connected.
 */
public interface CallHomeTopologyService {
    /**
     * Enable a node backed by a transport accepted from a call-home device. If a call-home node with the same
     * identifier already exists, it is disconnected and replaced.
     *
     * <p>If the node is accepted, {@code initializer} is invoked synchronously before this method returns. If the node
     * is rejected, for example because a node with the same identifier is configured in the topology,
     * {@code initializer} is not invoked at all.
     *
     * @param node synthetic node describing the call-home device and its connection parameters
     * @param sshAlg SSH algorithms negotiated with the device, {@code null} for TLS or if not available
     * @param initializer initializer of the NETCONF session on the accepted transport
     */
    void enableCallHomeNode(@NonNull Node node, @Nullable NegotiatedSshAlg sshAlg,
        @NonNull CallHomeSessionInitializer initializer);

    /**
     * Disable a node previously enabled through {@link #enableCallHomeNode(Node, NegotiatedSshAlg,
     * CallHomeSessionInitializer)}, removing its mount point and operational state. Nodes configured in the topology
     * are not affected. This method does nothing if no call-home node with the specified identifier exists.
     *
     * @param nodeId identifier of the call-home node
     */
    void disableCallHomeNode(@NonNull NodeId nodeId);
}
