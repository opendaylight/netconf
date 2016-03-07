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
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.api.services.RestconfOperationsService;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.rest.api.services.ServicesWrapper;
import org.opendaylight.restconf.rest.api.services.schema.SchemaService;
import org.opendaylight.restconf.rest.impl.services.schema.SchemaServiceImpl;

public class ServicesWrapperImpl implements ServicesWrapper {

    private final RestconfModulesService delegatingRestconfModulesService;
    private final RestconfOperationsService delegatingRestconfOperationsService;
    private final RestconfStreamsService delegatingRestconfStreamsService;
    private final SchemaService schemaService;

    public ServicesWrapperImpl(final RestSchemaController restSchemaController) {
        this.delegatingRestconfModulesService = new RestconfModulesServiceImpl(restSchemaController);
        this.delegatingRestconfOperationsService = new RestconfOperationsServiceImpl(restSchemaController);
        this.delegatingRestconfStreamsService = new RestconfStreamsServiceImpl(restSchemaController);
        this.schemaService = new SchemaServiceImpl(restSchemaController);
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        return this.delegatingRestconfModulesService.getModules(uriInfo);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        return this.delegatingRestconfModulesService.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        return this.delegatingRestconfModulesService.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return this.delegatingRestconfOperationsService.getOperations(uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        return this.delegatingRestconfOperationsService.getOperations(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        return this.delegatingRestconfStreamsService.getAvailableStreams(uriInfo);
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModuleId) {
        return this.schemaService.getSchema(mountAndModuleId);
    }

}
