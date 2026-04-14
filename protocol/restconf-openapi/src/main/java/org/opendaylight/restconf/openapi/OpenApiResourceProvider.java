/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * {@link WebHostResourceProvider} of OpenApi.
 */
@Singleton
@NonNullByDefault
@Component(immediate = true, service = WebHostResourceProvider.class,
    configurationPid = "org.opendaylight.restconf.nb.rfc8040")
public final class OpenApiResourceProvider implements WebHostResourceProvider, AutoCloseable {
    private static final String API_ROOT_PATH_PROP = "api-root-path";
    private static final String DEFAULT_API_ROOT_PATH = "restconf";

    private final OpenApiServiceImpl service;

    @Inject
    @Activate
    public OpenApiResourceProvider(@Reference final DOMSchemaService schemaService,
            @Reference final DOMMountPointService mountPointService, final Map<String, ?> properties) {
        service = new OpenApiServiceImpl(schemaService, mountPointService, apiRootPath(properties));
    }

    @Override
    public String defaultPath() {
        return "openapi";
    }

    @Override
    public WebHostResourceInstance createInstance(final String path) {
        return new OpenApiResourceInstance(path, service);
    }

    @Override
    @Deactivate
    public void close() {
        service.close();
    }

    private static String apiRootPath(final Map<String, ?> properties) {
        final var value = properties.get(API_ROOT_PATH_PROP);
        return value instanceof String str ? str : DEFAULT_API_ROOT_PATH;
    }
}
