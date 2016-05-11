/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.handlers.impl;

import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;

/**
 * Implementation of {@link DOMMountPointServiceHandler}
 *
 */
public class DOMMountPointServiceHandlerImpl implements DOMMountPointServiceHandler {

    private DOMMountPointService domMountPointService;

    @Override
    public DOMMountPointService getDOMMountPointService() {
        return this.domMountPointService;
    }

    @Override
    public void setDOMMountPointService(final DOMMountPointService domMountPointService) {
        this.domMountPointService = domMountPointService;

    }

}
