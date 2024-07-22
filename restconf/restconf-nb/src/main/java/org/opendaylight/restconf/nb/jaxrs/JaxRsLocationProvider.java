/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import java.net.URI;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * A promise that a {@link SSEStreamService} will be attached.
 */
@Singleton
@Component
@NonNullByDefault
public final class JaxRsLocationProvider implements LocationProvider {
    @Inject
    @Activate
    public JaxRsLocationProvider() {
        // Exposed for DI
    }

    @Override
    public URI baseStreamLocation(final URI restconfURI) throws URISyntaxException {
        final var scheme = restconfURI.getScheme();
        return new URI(scheme, restconfURI.getRawUserInfo(), restconfURI.getHost(), restconfURI.getPort(),
            restconfURI.getPath() + JaxRsRestconf.STREAMS_SUBPATH, null, null);
    }
}
