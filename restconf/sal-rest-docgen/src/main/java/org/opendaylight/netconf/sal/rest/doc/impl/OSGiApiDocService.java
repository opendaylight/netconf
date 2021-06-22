/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.impl;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = ApiDocService.class)
public class OSGiApiDocService implements ApiDocService {
    private final ApiDocService delegate;

    @Inject
    @Activate
    public OSGiApiDocService(final @Reference DOMSchemaService schemaService,
                             final @Reference DOMMountPointService mountPointService) {
        final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040 = new ApiDocGeneratorRFC8040(schemaService);
        delegate = new ApiDocServiceImpl(new MountPointSwaggerGeneratorRFC8040(schemaService, mountPointService),
                apiDocGeneratorRFC8040, new AllModulesDocGenerator(apiDocGeneratorRFC8040));
    }

    @Override
    public Response getAllModulesDoc(final UriInfo uriInfo) {
        return delegate.getAllModulesDoc(uriInfo);
    }

    @Override
    public Response getDocByModule(final String module, final String revision, final UriInfo uriInfo) {
        return delegate.getDocByModule(module, revision, uriInfo);
    }

    @Override
    public Response getApiExplorer(final UriInfo uriInfo) {
        return delegate.getApiExplorer(uriInfo);
    }

    @Override
    public Response getListOfMounts(final UriInfo uriInfo) {
        return delegate.getListOfMounts(uriInfo);
    }

    @Override
    public Response getMountDocByModule(final String instanceNum, final String module, final String revision, final UriInfo uriInfo) {
        return delegate.getMountDocByModule(instanceNum, module, revision, uriInfo);
    }

    @Override
    public Response getMountDoc(final String instanceNum, final UriInfo uriInfo) {
        return delegate.getMountDoc(instanceNum, uriInfo);
    }
}
