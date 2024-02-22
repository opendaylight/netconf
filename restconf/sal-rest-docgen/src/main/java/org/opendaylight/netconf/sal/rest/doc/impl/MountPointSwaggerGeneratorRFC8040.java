/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;

/**
 * MountPoint generator implementation for RFC 8040.
 *
 * @author Thomas Pantelis
 */
public class MountPointSwaggerGeneratorRFC8040 extends BaseYangSwaggerGeneratorRFC8040 implements AutoCloseable {
    private final MountPointSwagger mountPointSwagger;

    public MountPointSwaggerGeneratorRFC8040(final DOMSchemaService schemaService,
            final DOMMountPointService mountService, final String basePath) {
        super(Optional.of(schemaService), basePath);
        mountPointSwagger = new MountPointSwagger(schemaService, mountService, this);
        mountPointSwagger.init();
    }

    public MountPointSwagger getMountPointSwagger() {
        return mountPointSwagger;
    }

    @Override
    public void close() {
        mountPointSwagger.close();
    }
}
