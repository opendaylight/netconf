/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.yang.common.QName;

public final class NetconfDeviceCapabilities {
    private final Map<QName, FailureReason> unresolvedCapabilites;
    private final Set<AvailableCapability>  resolvedCapabilities;
    private final Set<AvailableCapability> nonModuleBasedCapabilities;

    public NetconfDeviceCapabilities() {
        this.unresolvedCapabilites = new HashMap<>();
        this.resolvedCapabilities = new HashSet<>();
        this.nonModuleBasedCapabilities = new HashSet<>();
    }

    public void addUnresolvedCapability(QName source, FailureReason reason) {
        unresolvedCapabilites.put(source, reason);
    }

    public void addUnresolvedCapabilities(Collection<QName> capabilities, FailureReason reason) {
        for (QName s : capabilities) {
            unresolvedCapabilites.put(s, reason);
        }
    }

    public void addCapabilities(Collection<AvailableCapability>  availableSchemas) {
        resolvedCapabilities.addAll(availableSchemas);
    }

    public void addNonModuleBasedCapabilities(Collection<AvailableCapability> nonModuleCapabilities) {
        this.nonModuleBasedCapabilities.addAll(nonModuleCapabilities);
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

}
