/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.restconf.server.spi.RestconfStream.BaseUriProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * A promise that a {@link SSEStreamService} will be attached.
 */
@Singleton
@Component
@NonNullByDefault
public final class JaxRsBaseUriProvider implements BaseUriProvider {
    private static final URI STREAMS = URI.create(JaxRsRestconf.STREAMS_SUBPATH);
    private final WebServer webServer;
    private final JaxRsEndpointConfiguration jaxRsEndpointConfiguration;
    private static final String PROP_CONFIGURATION = ".configuration";

    @Inject
    @Activate
    public JaxRsBaseUriProvider(@Reference final WebServer webServer, final Map<String, ?> props) {
        // Exposed for DI
        this.webServer = webServer;
        this.jaxRsEndpointConfiguration = (JaxRsEndpointConfiguration) props.get(PROP_CONFIGURATION);
    }

    @Override
    public URI getRestconfUri() throws URISyntaxException {
        return new URI(webServer.getBaseURL() + jaxRsEndpointConfiguration.restconf() + "/");
    }
}
