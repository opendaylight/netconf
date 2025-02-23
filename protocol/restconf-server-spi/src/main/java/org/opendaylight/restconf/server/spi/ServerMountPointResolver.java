/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindPath.Data;
import org.opendaylight.restconf.server.api.ServerException;

/**
 * A resolver of {@code yang-ext:mount} references to {@link ServerStrategy}..
 */
@Beta
@NonNullByDefault
public interface ServerMountPointResolver {
    /**
     * Resolve a {@link ServerStrategy} for a {@code yang-ext:mount} path.
     *
     * @param mountPath mount point path
     * @return resolved {@link ServerStrategy}
     * @throws ServerException when an error occurs
     */
    ServerStrategy resolveMountPoint(Data mountPath) throws ServerException;
}
