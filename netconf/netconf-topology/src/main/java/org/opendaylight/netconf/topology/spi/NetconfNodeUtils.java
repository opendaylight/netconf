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
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.listener.UserPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

/**
 * Utility methods to work with {@link NetconfNode} information.
 */
public final class NetconfNodeUtils {
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
        return ipAddress != null ? new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(ipAddress), port)
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
        final var sessionPreferences = NetconfSessionPreferences.fromStrings(capabilities);
        final var nonModulePrefs = sessionPreferences.getNonModuleCaps();
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

        return new UserPreferences(NetconfSessionPreferences.fromStrings(capabilities, CapabilityOrigin.UserDefined),
            overrideYangModuleCaps, overrideNonModuleCaps);
    }
}
