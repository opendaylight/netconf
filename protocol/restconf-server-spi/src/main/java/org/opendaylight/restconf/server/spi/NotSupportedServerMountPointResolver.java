/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * A {@link ServerMountPointResolver} implementation fails all requests with {@link ErrorTag#OPERATION_NOT_SUPPORTED}.
 */
@NonNullByDefault
public final class NotSupportedServerMountPointResolver implements ServerMountPointResolver {
    public static final NotSupportedServerMountPointResolver INSTANCE = new NotSupportedServerMountPointResolver();

    private NotSupportedServerMountPointResolver() {
        // Hidden on purpose
    }

    @Override
    public ServerStrategy resolveMountPoint(final Data mountPath) throws ServerException {
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED, "Mount points not supported",
            new ErrorPath(mountPath));
    }
}
