/*
 * Copyright (c) 2018 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Application;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonToPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.PatchJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.PatchXmlBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlToPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesNotifWrapper;

@Singleton
public class RestconfNotifApplication extends Application {

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler mountPointServiceHandler;
    private final ServicesNotifWrapper servicesNotifWrapper;

    @Inject
    public RestconfNotifApplication(SchemaContextHandler schemaContextHandler,
            DOMMountPointServiceHandler mountPointServiceHandler, ServicesNotifWrapper servicesNotifWrapper) {
        this.schemaContextHandler = schemaContextHandler;
        this.mountPointServiceHandler = mountPointServiceHandler;
        this.servicesNotifWrapper = servicesNotifWrapper;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>>builder()
                .add(NormalizedNodeJsonBodyWriter.class)
                .add(NormalizedNodeXmlBodyWriter.class)
                .add(SchemaExportContentYinBodyWriter.class)
                .add(SchemaExportContentYangBodyWriter.class)
                .add(PatchJsonBodyWriter.class)
                .add(PatchXmlBodyWriter.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(servicesNotifWrapper);
        singletons.add(new JsonNormalizedNodeBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new JsonToPatchBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new XmlNormalizedNodeBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new XmlToPatchBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new RestconfDocumentedExceptionMapper(schemaContextHandler));
        return singletons;
    }
}
