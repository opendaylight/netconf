/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import java.util.Map;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointChild;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContextFactory;
import org.opendaylight.yangtools.rfc8528.data.api.YangLibraryConstants.ContainerName;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;

// TODO: this should really come from mdsal-yanglib-rfc8525
final class NetconfMountPointContextFactory implements MountPointContextFactory {
    private final MountPointContext mountPoint;

    NetconfMountPointContextFactory(final SchemaContext schemaContext) {
        mountPoint = new EmptyMountPointContext(schemaContext);
    }

    @Override
    public MountPointContext createContext(final Map<ContainerName, MountPointChild> libraryContainers,
            final MountPointChild schemaMounts) throws YangParserException {
        return mountPoint;
    }
}
