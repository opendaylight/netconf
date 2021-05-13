/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;

/**
 * Implementation of {@link DOMMountPointServiceHandler}.
 */
// FIXME: remove this class
@Singleton
public final class DOMMountPointServiceHandler {
    private final DOMMountPointService domMountPointService;

    /**
     * Prepare mount point service for Restconf services.
     *
     * @param domMountPointService
     *             mount point service
     */
    @Inject
    public DOMMountPointServiceHandler(final @Reference DOMMountPointService domMountPointService) {
        this.domMountPointService = requireNonNull(domMountPointService);
    }

    public @NonNull DOMMountPointService get() {
        return this.domMountPointService;
    }
}
