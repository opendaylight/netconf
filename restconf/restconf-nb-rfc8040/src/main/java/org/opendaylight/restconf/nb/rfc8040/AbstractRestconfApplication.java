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
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YangSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YinSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchBodyReader;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch.XmlPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;

/**
 * Abstract Restconf Application.
 */
abstract class AbstractRestconfApplication extends Application {
    private final SchemaContextHandler schemaContextHandler;
    private final ParserIdentifier parserIdentifier;
    private final List<Object> services;

    AbstractRestconfApplication(final SchemaContextHandler schemaContextHandler,
                                final ParserIdentifier parserIdentifier, final List<Object> services) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.parserIdentifier = parserIdentifier;
        this.services = requireNonNull(services);
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
        return ImmutableSet.builderWithExpectedSize(services.size() + 5)
            .addAll(services)
            .add(new JsonNormalizedNodeBodyReader(parserIdentifier))
            .add(new JsonPatchBodyReader(parserIdentifier))
            .add(new XmlNormalizedNodeBodyReader(parserIdentifier))
            .add(new XmlPatchBodyReader(parserIdentifier))
            .add(new RestconfDocumentedExceptionMapper(schemaContextHandler))
            .build();
    }
}
