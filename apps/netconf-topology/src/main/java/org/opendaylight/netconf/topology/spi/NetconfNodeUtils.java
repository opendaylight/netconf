/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.mdsal.UserPreferences;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

/**
 * Utility methods to work with {@link NetconfNode} information.
 */
public final class NetconfNodeUtils {
    // FIXME: extract all of this to users, as they are in control of topology-id
    @Deprecated(forRemoval = true)
    public static final String DEFAULT_TOPOLOGY_NAME = TopologyNetconf.QNAME.getLocalName();

    // FIXME: extract this into caller and pass to constructor
    @Deprecated(forRemoval = true)
    public static final KeyedInstanceIdentifier<Topology, TopologyKey> DEFAULT_TOPOLOGY_IID =
        InstanceIdentifier.create(NetworkTopology.class)
        .child(Topology.class, new TopologyKey(new TopologyId(DEFAULT_TOPOLOGY_NAME)));

    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();
    // FIXME: push this out to callers
    private static final YangInstanceIdentifier DEFAULT_TOPOLOGY_NODE = YangInstanceIdentifier.builder()
        .node(NetworkTopology.QNAME).node(Topology.QNAME)
        .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), DEFAULT_TOPOLOGY_NAME)
        .node(Node.QNAME)
        .build();

    private NetconfNodeUtils() {
        // Hidden on purpose
    }

    /**
     * Create an {@link InetSocketAddress} targeting a particular {@link NetconfNode}.
     *
     * @param node A {@link NetconfNode}
     * @return A {@link InetSocketAddress}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static @NonNull InetSocketAddress toInetSocketAddress(final NetconfNode node) {
        final var host = node.requireHost();
        final int port = node.requirePort().getValue().toJava();
        final var ipAddress = host.getIpAddress();
        return ipAddress != null ? new InetSocketAddress(IetfInetUtil.inetAddressFor(ipAddress), port)
            : new InetSocketAddress(host.getDomainName().getValue(), port);
    }

    public static @NonNull RemoteDeviceId toRemoteDeviceId(final NodeId nodeId, final NetconfNode node) {
        return new RemoteDeviceId(nodeId.getValue(), toInetSocketAddress(node));
    }

    /**
     * Extract {@link UserPreferences} from na {@link NetconfNode}.
     *
     * @param node A {@link NetconfNode}
     * @return {@link UserPreferences}, potentially {@code null}
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws IllegalArgumentException there are any non-module capabilities
     */
    public static @Nullable UserPreferences extractUserCapabilities(final NetconfNode node) {
        final var moduleCaps = node.getYangModuleCapabilities();
        final var nonModuleCaps = node.getNonModuleCapabilities();

        if (moduleCaps == null && nonModuleCaps == null) {
            // if none of yang-module-capabilities or non-module-capabilities is specified
            return null;
        }

        final var capabilities = new ArrayList<String>();
        final boolean overrideYangModuleCaps;
        if (moduleCaps != null) {
            capabilities.addAll(moduleCaps.getCapability());
            overrideYangModuleCaps = moduleCaps.getOverride();
        } else {
            overrideYangModuleCaps = false;
        }

        //non-module capabilities should not exist in yang module capabilities
        final var sessionPreferences = NetconfSessionPreferences.fromStrings(capabilities,
            CapabilityOrigin.DeviceAdvertised, node.getSessionId());
        final var nonModulePrefs = sessionPreferences.nonModuleCaps();
        if (!nonModulePrefs.isEmpty()) {
            throw new IllegalArgumentException("List yang-module-capabilities/capability should contain only module "
                + "based capabilities. Non-module capabilities used: " + nonModulePrefs);
        }

        final boolean overrideNonModuleCaps;
        if (nonModuleCaps != null) {
            capabilities.addAll(nonModuleCaps.getCapability());
            overrideNonModuleCaps = nonModuleCaps.getOverride();
        } else {
            overrideNonModuleCaps = false;
        }

        // FIXME: UserPreferences is constructor parameter of NetconfDeviceCommunicator and NetconfSessionPreferences
        // are created in NetconfDeviceCommunicator#onSessionUp from session. What are we doing here?
        // IMO we should rework UserPreferences and NetconfSessionPreferences and this method.
        return new UserPreferences(NetconfSessionPreferences.fromStrings(capabilities, CapabilityOrigin.UserDefined,
                node.getSessionId()), overrideYangModuleCaps, overrideNonModuleCaps);
    }

    @Deprecated(forRemoval = true)
    public static @NonNull YangInstanceIdentifier defaultTopologyMountPath(final RemoteDeviceId id) {
        return DEFAULT_TOPOLOGY_NODE.node(NodeIdentifierWithPredicates.of(Node.QNAME, NODE_ID_QNAME, id.name()));
    }
}
