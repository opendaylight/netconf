/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.listener;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.yang.common.QName;

// FIXME: this should be a record
public final class NetconfDeviceCapabilities {
    private static final @NonNull NetconfDeviceCapabilities EMPTY = new NetconfDeviceCapabilities(
        ImmutableMap.of(), ImmutableSet.of(), ImmutableSet.of());

    private final ImmutableMap<QName, FailureReason> unresolvedCapabilites;
    private final ImmutableSet<AvailableCapability> resolvedCapabilities;
    private final ImmutableSet<AvailableCapability> nonModuleBasedCapabilities;

    private NetconfDeviceCapabilities(final ImmutableMap<QName, FailureReason> unresolvedCapabilites,
            final ImmutableSet<AvailableCapability> resolvedCapabilities,
            final ImmutableSet<AvailableCapability> nonModuleBasedCapabilities) {
        this.unresolvedCapabilites = requireNonNull(unresolvedCapabilites);
        this.resolvedCapabilities = requireNonNull(resolvedCapabilities);
        this.nonModuleBasedCapabilities = requireNonNull(nonModuleBasedCapabilities);
    }

    public static @NonNull NetconfDeviceCapabilities empty() {
        return EMPTY;
    }

    public Set<AvailableCapability> getNonModuleBasedCapabilities() {
        return nonModuleBasedCapabilities;
    }

    public Map<QName, FailureReason> getUnresolvedCapabilites() {
        return unresolvedCapabilites;
    }

    public Set<AvailableCapability>  getResolvedCapabilities() {
        return resolvedCapabilities;
    }

//    public void addUnresolvedCapability(final QName source, final FailureReason reason) {
//        unresolvedCapabilites.put(source, reason);
//    }
//
//    public void addUnresolvedCapabilities(final Collection<QName> capabilities, final FailureReason reason) {
//        for (QName s : capabilities) {
//            unresolvedCapabilites.put(s, reason);
//        }
//    }
//
//    public void addCapabilities(final Collection<AvailableCapability>  availableSchemas) {
//        resolvedCapabilities.addAll(availableSchemas);
//    }
//
//    public void addNonModuleBasedCapabilities(final Collection<AvailableCapability> nonModuleCapabilities) {
//        nonModuleBasedCapabilities.addAll(nonModuleCapabilities);
//    }
}
