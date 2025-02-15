/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * A promise that a {@link JaxRsRestconf} will be attached, providing stream access.
 */
@Singleton
@Component
@NonNullByDefault
public final class JaxRsLocationProvider implements LocationProvider {
    private static final URI STREAMS = URI.create(JaxRsRestconf.STREAMS_SUBPATH);
    private static final URI RELATIVE_STREAMS = URI.create("/" + JaxRsRestconf.STREAMS_SUBPATH);

    @Inject
    @Activate
    public JaxRsLocationProvider() {
        // Exposed for DI
    }

    @Override
    public URI baseStreamLocation(final URI restconfURI) {
        return restconfURI.resolve(STREAMS);
    }

    @Override
    public URI relativeStreamLocation() {
        return RELATIVE_STREAMS;
    }
}
