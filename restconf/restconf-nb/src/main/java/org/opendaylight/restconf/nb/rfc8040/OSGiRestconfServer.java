/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.opendaylight.restconf.nb.netty.NettyRestconf;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = { OSGiRestconfServer.class })
public final class OSGiRestconfServer {

    private final NettyNorthboundInitializer initializer;

    @Activate
    public OSGiRestconfServer(@Reference final RestconfServer restconfServer) {
        initializer = new NettyNorthboundInitializer(new NettyRestconf(restconfServer));

    }

    public NettyNorthboundInitializer initializer() {
        return initializer;
    }
}
