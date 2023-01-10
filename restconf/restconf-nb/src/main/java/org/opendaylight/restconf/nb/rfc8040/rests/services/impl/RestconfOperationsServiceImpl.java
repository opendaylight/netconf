/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import javax.ws.rs.Path;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfOperationsService}.
 */
@Path("/")
public class RestconfOperationsServiceImpl implements RestconfOperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfOperationsServiceImpl.class);

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointService mountPointService;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}.
     *
     * @param schemaContextHandler handling schema context
     * @param mountPointService handling dom mount point service
     */
    public RestconfOperationsServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.mountPointService = requireNonNull(mountPointService);
    }

    @Override
    public String getOperationsJSON() {
        return OperationsContent.JSON.bodyFor(schemaContextHandler.get());
    }

    @Override
    public String getOperationJSON(final String identifier) {
        final var identifierContext = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextHandler.get(), Optional.of(mountPointService));
        return OperationsContent.JSON.bodyFor(identifierContext);
    }

    @Override
    public String getOperationsXML() {
        return OperationsContent.XML.bodyFor(schemaContextHandler.get());
    }

    @Override
    public String getOperationXML(final String identifier) {
        final var identifierContext = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextHandler.get(), Optional.of(mountPointService));
        return OperationsContent.XML.bodyFor(identifierContext);
    }
}
