/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfSchemaService;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link RestconfSchemaService}.
 */
@Path("/")
public class RestconfSchemaServiceImpl implements RestconfSchemaService {
    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler domMountPointServiceHandler;
    private final DOMYangTextSourceProvider sourceProvider;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}
     * .
     *
     * @param schemaContextHandler handling schema context
     * @param domMountPointServiceHandler handling dom mount point service
     */
    public RestconfSchemaServiceImpl(final SchemaContextHandler schemaContextHandler,
                                     final DOMMountPointServiceHandler domMountPointServiceHandler,
                                     final DOMYangTextSourceProvider sourceProvider) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.domMountPointServiceHandler = requireNonNull(domMountPointServiceHandler);
        this.sourceProvider = requireNonNull(sourceProvider);
    }

    @Override
    public SchemaExportContext getSchema(final String identifier) {
        return ParserIdentifier.toSchemaExportContextFromIdentifier(this.schemaContextHandler.get(), identifier,
                this.domMountPointServiceHandler.get(), sourceProvider);
    }
}
