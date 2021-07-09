/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YangSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YinSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonToPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.PatchJsonBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.PatchXmlBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlToPatchBodyReader;

/**
 * Abstract Restconf Application.
 */
abstract class AbstractRestconfApplication extends Application {
    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointService mountPointService;
    private final List<Object> services;

    AbstractRestconfApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService, final List<Object> services) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.mountPointService = requireNonNull(mountPointService);
        this.services = requireNonNull(services);
    }

    @Override
    public final Set<Class<?>> getClasses() {
        return Set.of(
            NormalizedNodeJsonBodyWriter.class, NormalizedNodeXmlBodyWriter.class,
            YinSchemaExportBodyWriter.class, YangSchemaExportBodyWriter.class,
            PatchJsonBodyWriter.class, PatchXmlBodyWriter.class);
    }

    @Override
    public final Set<Object> getSingletons() {
        return ImmutableSet.<Object>builderWithExpectedSize(services.size() + 5)
            .addAll(services)
            .add(new JsonNormalizedNodeBodyReader(schemaContextHandler, mountPointService))
            .add(new JsonToPatchBodyReader(schemaContextHandler, mountPointService))
            .add(new XmlNormalizedNodeBodyReader(schemaContextHandler, mountPointService))
            .add(new XmlToPatchBodyReader(schemaContextHandler, mountPointService))
            .add(new RestconfDocumentedExceptionMapper(schemaContextHandler))
            .build();
    }
}
