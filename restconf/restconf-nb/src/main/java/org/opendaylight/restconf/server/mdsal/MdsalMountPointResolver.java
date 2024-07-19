/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.ServerStrategy;

/**
 * A resolver of {@code yang-ext:mount} references based on {@link DOMMountPointService}.
 */
@NonNullByDefault
public record MdsalMountPointResolver(DOMMountPointService mountPointService) {
    public MdsalMountPointResolver {
        requireNonNull(mountPointService);
    }

    public ServerStrategy createStrategy(final DatabindContext databind, final ApiPath mountPath)
            throws ServerException {
        // FIXME: impl
        throw new UnsupportedOperationException();
    }
}
