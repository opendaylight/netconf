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
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.OperationsContent;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.OperationsResourceUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
    public String getOperationsXML() {
        return OperationsContent.XML.bodyFor(schemaContextHandler.get());
    }

    @Override
    public NormalizedNodePayload getOperations(final String identifier, final UriInfo uriInfo) {
        if (!identifier.contains(RestconfConstants.MOUNT)) {
            final String errMsg = "URI has bad format. If operations behind mount point should be showed, URI has to "
                    + " end with " + RestconfConstants.MOUNT;
            LOG.debug("{} for {}", errMsg, identifier);
            throw new RestconfDocumentedException(errMsg + RestconfConstants.MOUNT, ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext<?> mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
            schemaContextHandler.get(), Optional.of(mountPointService),
                Optional.of(schemaContextHandler.getDataBroker()));
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        final var entry = OperationsResourceUtils.contextForModelContext(modelContext(mountPoint), mountPoint);
        return NormalizedNodePayload.of(entry.getKey(), entry.getValue());
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
