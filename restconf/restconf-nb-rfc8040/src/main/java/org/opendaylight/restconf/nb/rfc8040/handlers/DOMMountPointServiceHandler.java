/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import java.util.Objects;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;

/**
 * Implementation of {@link DOMMountPointServiceHandler}.
 *
 */
public class DOMMountPointServiceHandler implements Handler<DOMMountPointService> {
    private final DOMMountPointService domMountPointService;

    /**
     * Prepare mount point service for Restconf services.
     *
     * @param domMountPointService
     *             mount point service
     */
    public DOMMountPointServiceHandler(final DOMMountPointService domMountPointService) {
        this.domMountPointService = Objects.requireNonNull(domMountPointService);
    }

    @Override
    public DOMMountPointService get() {
        return this.domMountPointService;
    }
}
