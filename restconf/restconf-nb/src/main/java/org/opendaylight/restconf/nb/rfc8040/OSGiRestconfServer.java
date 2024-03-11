/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service = { OSGiRestconfServer.class })
public final class OSGiRestconfServer {

    private final NettyNorthboundInitializer initializer;

    @Activate
    public OSGiRestconfServer() {
        initializer = new NettyNorthboundInitializer();

    }

    public NettyNorthboundInitializer initializer() {
        return initializer;
    }
}
