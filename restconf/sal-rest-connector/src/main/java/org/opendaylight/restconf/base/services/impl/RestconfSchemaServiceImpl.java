/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.impl;

import javax.ws.rs.Path;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.restconf.base.services.api.RestconfSchemaService;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link RestconfSchemaService}.
 *
 */
@Path("/")
public class RestconfSchemaServiceImpl implements RestconfSchemaService {

    private SchemaContextHandler schemaContextHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;
    private DOMYangTextSourceProvider sourceProvider;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}
     * .
     *
     * @param schemaContextHandler
     *             handling schema context
     * @param domMountPointServiceHandler
     *             handling dom mount point service
     */
    public RestconfSchemaServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler,
            final DOMYangTextSourceProvider sourceProvider) {
        this.schemaContextHandler = schemaContextHandler;
        this.domMountPointServiceHandler = domMountPointServiceHandler;
        this.sourceProvider = sourceProvider;
    }

    @Override
    public synchronized void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            } else if (object instanceof DOMMountPointServiceHandler) {
                domMountPointServiceHandler = (DOMMountPointServiceHandler) object;
            } else if (object instanceof DOMYangTextSourceProvider) {
                sourceProvider = (DOMYangTextSourceProvider) object;
            }
        }
    }

    @Override
    public SchemaExportContext getSchema(final String identifier) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        return ParserIdentifier.toSchemaExportContextFromIdentifier(schemaContextRef.get(), identifier,
                this.domMountPointServiceHandler.get(), sourceProvider);
    }
}
