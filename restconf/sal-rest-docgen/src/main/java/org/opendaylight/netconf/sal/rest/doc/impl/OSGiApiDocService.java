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

    private ApiDocService delegate;

    @Inject
    @Activate
    public OSGiApiDocService(final @Reference DOMSchemaService schemaService,
                             final @Reference DOMMountPointService mountPointService) {
        final ApiDocGeneratorDraftO2 apiDocGeneratorDraft02 = new ApiDocGeneratorDraftO2(schemaService);
        final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040 = new ApiDocGeneratorRFC8040(schemaService);
        delegate = new ApiDocServiceImpl(new MountPointSwaggerGeneratorDraft02(schemaService, mountPointService),
                new MountPointSwaggerGeneratorRFC8040(schemaService, mountPointService),
                apiDocGeneratorDraft02, apiDocGeneratorRFC8040,
                new AllModulesDocGenerator(apiDocGeneratorDraft02, apiDocGeneratorRFC8040));
    }

    @Override
    public Response getAllModulesDoc(UriInfo uriInfo) {
        return delegate.getAllModulesDoc(uriInfo);
    }

    @Override
    public Response getDocByModule(String module, String revision, UriInfo uriInfo) {
        return delegate.getDocByModule(module, revision, uriInfo);
    }

    @Override
    public Response getApiExplorer(UriInfo uriInfo) {
        return delegate.getApiExplorer(uriInfo);
    }

    @Override
    public Response getListOfMounts(UriInfo uriInfo) {
        return delegate.getListOfMounts(uriInfo);
    }

    @Override
    public Response getMountDocByModule(String instanceNum, String module, String revision, UriInfo uriInfo) {
        return delegate.getMountDocByModule(instanceNum, module, revision, uriInfo);
    }

    @Override
    public Response getMountDoc(String instanceNum, UriInfo uriInfo) {
        return delegate.getMountDoc(instanceNum, uriInfo);
    }
}
