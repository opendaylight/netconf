/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.yang.common.QName;

public record NetconfDeviceCapabilities(
        @NonNull ImmutableMap<QName, FailureReason> unresolvedCapabilites,
        @NonNull ImmutableSet<AvailableCapability> resolvedCapabilities,
        @NonNull ImmutableSet<AvailableCapability> nonModuleBasedCapabilities) {
    private static final @NonNull NetconfDeviceCapabilities EMPTY =
        new NetconfDeviceCapabilities(ImmutableMap.of(), ImmutableSet.of(), ImmutableSet.of());

    public NetconfDeviceCapabilities {
        requireNonNull(unresolvedCapabilites);
        requireNonNull(resolvedCapabilities);
        requireNonNull(nonModuleBasedCapabilities);
    }

    public static @NonNull NetconfDeviceCapabilities empty() {
        return EMPTY;
    }
}
