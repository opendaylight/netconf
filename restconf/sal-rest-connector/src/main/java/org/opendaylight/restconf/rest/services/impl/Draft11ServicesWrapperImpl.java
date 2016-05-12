/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.rest.services.api.Draft11ServicesWrapper;
import org.opendaylight.restconf.rest.services.api.RestconfModulesService;
import org.opendaylight.restconf.rest.services.api.RestconfOperationsService;
import org.opendaylight.restconf.rest.services.api.RestconfSchemaService;
import org.opendaylight.restconf.rest.services.api.RestconfStreamsService;

/**
 * Implementation of {@link Draft11ServicesWrapper}
 *
 */
public class Draft11ServicesWrapperImpl implements Draft11ServicesWrapper {

    private RestconfModulesService delegRestModService;
    private RestconfOperationsService delegRestOpsService;
    private RestconfStreamsService delegRestStrsService;
    private RestconfSchemaService delegRestSchService;

    private Draft11ServicesWrapperImpl() {
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

    public static Draft11ServicesWrapperImpl getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        public static final Draft11ServicesWrapperImpl INSTANCE = new Draft11ServicesWrapperImpl();
    }

    public void setHandlers(final SchemaContextHandler schemaCtxHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.delegRestModService = new RestconfModulesServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestOpsService = new RestconfOperationsServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestSchService = new RestconfSchemaServiceImpl(schemaCtxHandler, domMountPointServiceHandler);
        this.delegRestStrsService = new RestconfStreamsServiceImpl(schemaCtxHandler);
    }

}
