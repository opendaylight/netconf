/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.mountpoints.MountPointOpenApi;

/**
 * MountPoint generator implementation for RFC 8040.
 *
 * @author Thomas Pantelis
 */
public class MountPointOpenApiGeneratorRFC8040 extends BaseYangOpenApiGeneratorRFC8040 implements AutoCloseable {
    private final MountPointOpenApi mountPointOpenApi;

    public MountPointOpenApiGeneratorRFC8040(final @NonNull DOMSchemaService schemaService,
            final @NonNull DOMMountPointService mountService) {
        super(schemaService);
        mountPointOpenApi = new MountPointOpenApi(schemaService, mountService, this);
        mountPointOpenApi.init();
    }

    public MountPointOpenApiGeneratorRFC8040(final @NonNull DOMSchemaService schemaService,
            final @NonNull DOMMountPointService mountService, final @NonNull String basePath) {
        super(schemaService, basePath);
        mountPointOpenApi = new MountPointOpenApi(schemaService, mountService, this);
        mountPointOpenApi.init();
    }

    public MountPointOpenApi getMountPointOpenApi() {
        return mountPointOpenApi;
    }

    @Override
    public void close() {
        mountPointOpenApi.close();
    }
}
