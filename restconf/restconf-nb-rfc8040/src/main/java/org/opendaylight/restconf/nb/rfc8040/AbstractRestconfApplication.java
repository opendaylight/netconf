/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
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
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;

/**
 * Abstract Restconf Application.
 *
 * @param <T> {@link ServicesWrapper} or {@link ServicesNotifWrapper} implementation
 */
public abstract class AbstractRestconfApplication<T> extends Application {
    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointService mountPointService;
    private final T servicesWrapper;

    public AbstractRestconfApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService, final T servicesNotifWrapper) {
        this.schemaContextHandler = schemaContextHandler;
        this.mountPointService = mountPointService;
        this.servicesWrapper = servicesNotifWrapper;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>>builder()
                .add(NormalizedNodeJsonBodyWriter.class).add(NormalizedNodeXmlBodyWriter.class)
                .add(SchemaExportContentYinBodyWriter.class).add(SchemaExportContentYangBodyWriter.class)
                .add(PatchJsonBodyWriter.class).add(PatchXmlBodyWriter.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(servicesWrapper);
        singletons.add(new JsonNormalizedNodeBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new JsonToPatchBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new XmlNormalizedNodeBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new XmlToPatchBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new RestconfDocumentedExceptionMapper(schemaContextHandler));
        return singletons;
    }
}
