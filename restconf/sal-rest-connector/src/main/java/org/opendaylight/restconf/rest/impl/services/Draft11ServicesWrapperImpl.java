/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.Draft11ServicesWrapper;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.api.services.RestconfOperationsService;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.rest.api.services.schema.RestconfSchemaService;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link Draft11ServicesWrapper}
 *
 */
public class Draft11ServicesWrapperImpl implements Draft11ServicesWrapper {

    private final RestconfModulesService delegRestModService;
    private final RestconfOperationsService delegRestOpsService;
    private final RestconfStreamsService delegRestStrsService;
    private final RestconfSchemaService delegRestSchService;

    /**
     * Creating delegates to all implemented services
     *
     * @param schemaContextHandler
     *            - for handling {@link SchemaContext}
     * @param domMountPointServiceHandler
     *            - for handling {@link DOMMountPointServiceHandler}
     */
    public Draft11ServicesWrapperImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.delegRestModService = new RestconfModulesServiceImpl(schemaContextHandler, domMountPointServiceHandler);
        this.delegRestOpsService = new RestconfOperationsServiceImpl(schemaContextHandler, domMountPointServiceHandler);
        this.delegRestStrsService = new RestconfStreamsServiceImpl(schemaContextHandler);
        this.delegRestSchService = new RestconfSchemaServiceImpl(schemaContextHandler, domMountPointServiceHandler);
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        return this.delegRestModService.getModules(uriInfo);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModService.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModService.getModule(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return this.delegRestOpsService.getOperations(uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        return this.delegRestOpsService.getOperations(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        return this.delegRestStrsService.getAvailableStreams(uriInfo);
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModuleId) {
        return this.delegRestSchService.getSchema(mountAndModuleId);
    }

}
