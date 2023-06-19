/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * {@link NetconfDeviceCapabilities} and {@link EffectiveModelContext} pertaining to a {@link NetconfDevice}.
 */
public record NetconfDeviceSchema(
    @NonNull NetconfDeviceCapabilities capabilities,
    @NonNull MountPointContext mountContext) {

    public NetconfDeviceSchema {
        requireNonNull(capabilities);
        requireNonNull(mountContext);
    }
}
