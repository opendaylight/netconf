/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YangSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YinSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RequestLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.ResponseWithBodyLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.ResponseWithoutBodyLogger;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RestconfLoggingBroker;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RestconfLoggingConfiguration;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchStatusBodyWriter;

/**
 * Abstract Restconf Application.
 */
abstract class AbstractRestconfApplication extends Application {
    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointService mountPointService;
    private final List<Object> services;
    private final RestconfLoggingConfiguration restconfLoggingConfiguration;

    AbstractRestconfApplication(final SchemaContextHandler schemaContextHandler,
                                final DOMMountPointService mountPointService, final List<Object> services,
                                final RestconfLoggingConfiguration restconfLoggingConfiguration) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.mountPointService = requireNonNull(mountPointService);
        this.services = requireNonNull(services);
        this.restconfLoggingConfiguration = requireNonNull(restconfLoggingConfiguration);
    }

    @Override
    public final Set<Class<?>> getClasses() {
        return Set.of(
            JsonNormalizedNodeBodyWriter.class, XmlNormalizedNodeBodyWriter.class,
            YinSchemaExportBodyWriter.class, YangSchemaExportBodyWriter.class,
            JsonPatchStatusBodyWriter.class, XmlPatchStatusBodyWriter.class);
    }

    @Override
    public final Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>(services);
        singletons.add(new JsonNormalizedNodeBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new JsonPatchBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new XmlNormalizedNodeBodyReader(schemaContextHandler, mountPointService));
        singletons.add(new XmlPatchBodyReader(schemaContextHandler, mountPointService));
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
