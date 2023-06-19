/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointChild;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContextFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

// TODO: this should really come from mdsal-yanglib-rfc8525
final class NetconfMountPointContextFactory implements MountPointContextFactory {
    private final @NonNull MountPointContext mountPoint;

    NetconfMountPointContextFactory(final EffectiveModelContext schemaContext) {
        mountPoint = MountPointContext.of(schemaContext);
    }

    @Override
    public MountPointContext createContext(final Map<ContainerName, MountPointChild> libraryContainers,
            final MountPointChild schemaMounts) {
        return mountPoint;
    }
}
