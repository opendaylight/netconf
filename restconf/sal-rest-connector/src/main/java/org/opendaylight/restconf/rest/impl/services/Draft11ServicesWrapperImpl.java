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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link ServicesWrapper}
 *
 */
public class Draft11ServicesWrapperImpl implements Draft11ServicesWrapper {

    private final RestconfModulesService delegRestModSer;
    private final RestconfOperationsService delegRestOpsSer;
    private final RestconfStreamsService delegRestStrsSer;
    private final RestconfSchemaService delegRestSchSer;

    /**
     * Creating delegates to all implemented services
     *
     * @param schemaContextHandler
     *            - for handling {@link SchemaContext}
     */
    public Draft11ServicesWrapperImpl(final SchemaContextHandler schemaContextHandler) {
        this.delegRestModSer = new RestconfModulesServiceImpl(schemaContextHandler);
        this.delegRestOpsSer = new RestconfOperationsServiceImpl(schemaContextHandler);
        this.delegRestStrsSer = new RestconfStreamsServiceImpl(schemaContextHandler);
        this.delegRestSchSer = new RestconfSchemaServiceImpl(schemaContextHandler);
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        return this.delegRestModSer.getModules(uriInfo);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModSer.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        return this.delegRestModSer.getModules(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return this.delegRestOpsSer.getOperations(uriInfo);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        return this.delegRestOpsSer.getOperations(identifier, uriInfo);
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        return this.delegRestStrsSer.getAvailableStreams(uriInfo);
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModuleId) {
        return this.delegRestSchSer.getSchema(mountAndModuleId);
    }

}
