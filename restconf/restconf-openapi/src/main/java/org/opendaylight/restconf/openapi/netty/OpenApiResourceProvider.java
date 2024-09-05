/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * {@link WebHostResourceProvider} of OpenApi.
 */
@Singleton
@NonNullByDefault
@Component(immediate = true)
public final class OpenApiResourceProvider implements WebHostResourceProvider {
    private final OpenApiService service;

    @Inject
    @Activate
    public OpenApiResourceProvider(@Reference final OpenApiService service) {
        this.service = requireNonNull(service);
    }

    @Override
    public String defaultPath() {
        return "openapi";
    }

    @Override
    public WebHostResourceInstance createInstance(final String path) {
        return new OpenApiResourceInstance(path, service);
    }
}
