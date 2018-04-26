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
public final class DOMMountPointServiceHandler implements Handler<DOMMountPointService> {
    private static final DOMMountPointServiceHandler INSTANCE = new DOMMountPointServiceHandler();

    private DOMMountPointService domMountPointService;

    /**
     * Prepare mount point service for Restconf services.
     *
     * @param domMountPointService
     *             mount point service
     */
    private DOMMountPointServiceHandler(final DOMMountPointService domMountPointService) {
        this.domMountPointService = Objects.requireNonNull(domMountPointService);
    }

    @Deprecated
    private DOMMountPointServiceHandler() {
    }

    @Deprecated
    public static DOMMountPointServiceHandler instance() {
        return INSTANCE;
    }

    public static DOMMountPointServiceHandler newInstance(DOMMountPointService domMountPointService) {
        INSTANCE.domMountPointService = domMountPointService;
        return INSTANCE;
    }

    @Override
    public DOMMountPointService get() {
        return this.domMountPointService;
    }
}
