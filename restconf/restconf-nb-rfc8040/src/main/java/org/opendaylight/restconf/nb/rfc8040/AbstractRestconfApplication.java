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
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RequestLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.ResponseWithBodyLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.ResponseWithoutBodyLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RestconfLoggingBroker;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RestconfLoggingConfiguration;
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
    private final DOMMountPointServiceHandler mountPointServiceHandler;
    private final T servicesWrapper;
    private final RestconfLoggingConfiguration restconfLoggingConfiguration;

    public AbstractRestconfApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler, final T servicesNotifWrapper,
            final RestconfLoggingConfiguration restconfLoggingConfiguration) {
        this.schemaContextHandler = schemaContextHandler;
        this.mountPointServiceHandler = mountPointServiceHandler;
        this.servicesWrapper = servicesNotifWrapper;
        this.restconfLoggingConfiguration = restconfLoggingConfiguration;
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
        singletons.add(new JsonNormalizedNodeBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new JsonToPatchBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new XmlNormalizedNodeBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new XmlToPatchBodyReader(schemaContextHandler, mountPointServiceHandler));
        singletons.add(new RestconfDocumentedExceptionMapper(schemaContextHandler));
        if (restconfLoggingConfiguration.isRestconfLoggingEnabled()) {
            final RestconfLoggingBroker restconfLoggingBroker = new RestconfLoggingBroker(restconfLoggingConfiguration);
            singletons.add(new RequestLogger(restconfLoggingBroker));
            singletons.add(new ResponseWithoutBodyLogger(restconfLoggingBroker));
            singletons.add(new ResponseWithBodyLogger(restconfLoggingBroker));
        }
        return singletons;
    }
}
