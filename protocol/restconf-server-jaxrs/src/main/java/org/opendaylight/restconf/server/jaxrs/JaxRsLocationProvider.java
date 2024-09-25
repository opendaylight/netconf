/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * A promise that a {@link JaxRsRestconf} will be attached, providing stream access.
 */
@Singleton
@Component
@NonNullByDefault
public final class JaxRsLocationProvider implements LocationProvider {
    private final WebServer webServer;
    private final JaxRsEndpointConfiguration configuration;

    @Inject
    @Activate
    public JaxRsLocationProvider(@Reference final WebServer webServer, final @Reference JaxRsEndpoint jaxrsEndpoint) {
        this.webServer = webServer;
        configuration = jaxrsEndpoint.configuration();
    }

    @Override
    public String baseStreamLocation() {
        return webServer.getBaseURL() + "/" + configuration.restconf() + JaxRsRestconf.STREAMS_SUBPATH;
    }
}
