/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfOperationsService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link RestconfOperationsService}
 *
 */
public class RestconfOperationsServiceImpl implements RestconfOperationsService {

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler domMountPointServiceHandler;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}
     *
     * @param schemaContextHandler
     *            - handling schema context
     * @param domMountPointServiceHandler
     *            - handling dom mount point service
     */
    public RestconfOperationsServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.schemaContextHandler = schemaContextHandler;
        this.domMountPointServiceHandler = domMountPointServiceHandler;
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        throw new RestconfDocumentedException("Not yet implemented.", new UnsupportedOperationException());
    }


    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        throw new RestconfDocumentedException("Not yet implemented.", new UnsupportedOperationException());
    }

}
