/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;

/**
 * {@link NetconfDeviceCapabilities} and {@link DatabindContext} pertaining to a {@link NetconfDevice}.
 */
@NonNullByDefault
public record NetconfDeviceSchema(DatabindContext databind, NetconfDeviceCapabilities capabilities) {
    public NetconfDeviceSchema {
        requireNonNull(databind);
        requireNonNull(capabilities);
    }
}
