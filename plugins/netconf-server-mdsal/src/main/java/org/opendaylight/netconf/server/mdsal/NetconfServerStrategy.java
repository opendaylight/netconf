/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.server.api.monitoring.Capability;

/**
 * Strategy for executing NETCONF server requests. It is bound to a {@link DatabindContext} and has capabilities derived
 * from it.
 *
 * @param databind the databind
 * @param capabilities the capabilities
 */
@NonNullByDefault
record NetconfServerStrategy(DatabindContext databind, ImmutableSet<Capability> capabilities) {
    NetconfServerStrategy {
        requireNonNull(databind);
        requireNonNull(capabilities);
    }
}
