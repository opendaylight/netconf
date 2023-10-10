/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.MountPointInstance;
import org.opendaylight.restconf.openapi.mountpoints.MountPointOpenApi;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


/**
 * This service generates swagger (See
 * <a href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 */
@Component
@Singleton
public final class OpenApiServiceImpl implements OpenApiService {
    // FIXME: make this configurable
    public static final int DEFAULT_PAGESIZE = 20;

    // Query parameter
    private static final String PAGE_NUM = "pageNum";

    private final MountPointOpenApi mountPointOpenApiRFC8040;
    private final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040;

    @Inject
    @Activate
    public OpenApiServiceImpl(final @Reference DOMSchemaService schemaService,
                             final @Reference DOMMountPointService mountPointService) {
        this(new MountPointOpenApiGeneratorRFC8040(schemaService, mountPointService),
            new OpenApiGeneratorRFC8040(schemaService));
    }

    public OpenApiServiceImpl(final DOMSchemaService schemaService,
                             final DOMMountPointService mountPointService,
                             final String basePath) {
        this(new MountPointOpenApiGeneratorRFC8040(schemaService, mountPointService, basePath),
            new OpenApiGeneratorRFC8040(schemaService, basePath));
    }

    @VisibleForTesting
    OpenApiServiceImpl(final MountPointOpenApiGeneratorRFC8040 mountPointOpenApiGeneratorRFC8040,
                       final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040) {
        mountPointOpenApiRFC8040 = requireNonNull(mountPointOpenApiGeneratorRFC8040).getMountPointOpenApi();
        this.openApiGeneratorRFC8040 = requireNonNull(openApiGeneratorRFC8040);
    }

    @Override
    public Response getAllModulesDoc(final UriInfo uriInfo) throws IOException {
        final OpenApiInputStream stream = openApiGeneratorRFC8040.getControllerModulesDoc(uriInfo);
        return Response.ok(stream).build();
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public Response getDocByModule(final String module, final String revision, final UriInfo uriInfo)
            throws IOException {
        final OpenApiInputStream stream = openApiGeneratorRFC8040.getApiDeclaration(module, revision, uriInfo);
        return Response.ok(stream).build();
    }

    /**
     * Redirects to embedded swagger ui.
     */
    @Override
    public Response getApiExplorer(final UriInfo uriInfo) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("../explorer/index.html").build()).build();
    }

    @Override
    public Response getListOfMounts(final UriInfo uriInfo) {
        final List<MountPointInstance> entity = mountPointOpenApiRFC8040
                .getInstanceIdentifiers().entrySet().stream()
                .map(entry -> new MountPointInstance(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return Response.ok(entity).build();
    }

    @Override
    public Response getMountDocByModule(final String instanceNum, final String module,
            final String revision, final UriInfo uriInfo) throws IOException {
        final OpenApiInputStream stream =
            mountPointOpenApiRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum), module, revision);
        return Response.ok(stream).build();
    }

    @Override
    public Response getMountDoc(final String instanceNum, final UriInfo uriInfo) throws IOException {
        final String stringPageNum = uriInfo.getQueryParameters().getFirst(PAGE_NUM);
        final OpenApiInputStream stream =
            mountPointOpenApiRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum), stringPageNum);
        return Response.ok(stream).build();
    }
}
