/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

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
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * {@link WebHostResourceProvider} of OpenApi.
 */
@Singleton
@NonNullByDefault
@Component(immediate = true, service = WebHostResourceProvider.class,
    configurationPid = "org.opendaylight.restconf.nb.rfc8040")
@Designate(ocd = OpenApiResourceProvider.Configuration.class)
public final class OpenApiResourceProvider implements WebHostResourceProvider, AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "1", description = """
            The URI path of the RESTCONF API root resource. This value will be used as the result of {+restconf} URI
            Template.
            """)
        String api$_$root$_$path() default "restconf";
    }

    private final OpenApiServiceImpl service;

    @Inject
    @Activate
    public OpenApiResourceProvider(@Reference final DOMSchemaService schemaService,
            @Reference final DOMMountPointService mountPointService, final Configuration configuration) {
        service = new OpenApiServiceImpl(schemaService, mountPointService,
            configuration.api$_$root$_$path());
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
}
