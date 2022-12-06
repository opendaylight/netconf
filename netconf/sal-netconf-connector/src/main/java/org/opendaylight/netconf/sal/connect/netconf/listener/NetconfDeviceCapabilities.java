/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.yang.common.QName;

public final class NetconfDeviceCapabilities {
    // FIXME: NETCONF-920: These should be immutable
    private final Map<QName, FailureReason> unresolvedCapabilites = new HashMap<>();
    private final Set<AvailableCapability> resolvedCapabilities = new HashSet<>();
    private final Set<AvailableCapability> nonModuleBasedCapabilities  = new HashSet<>();

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
