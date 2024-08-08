/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.MountPointInstance;
import org.opendaylight.restconf.openapi.mountpoints.MountPointOpenApi;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpoint;
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
    private final MountPointOpenApi mountPointOpenApiRFC8040;
    private final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040;

    @Inject
    @Activate
    public OpenApiServiceImpl(final @Reference DOMSchemaService schemaService,
            final @Reference DOMMountPointService mountPointService,
            final @Reference JaxRsEndpoint jaxrsEndpoint) {
        this(schemaService, mountPointService, jaxrsEndpoint.configuration().restconf());
    }

    private OpenApiServiceImpl(final DOMSchemaService schemaService, final DOMMountPointService mountPointService,
            final @NonNull String restconf) {
        this(new MountPointOpenApiGeneratorRFC8040(schemaService, mountPointService, restconf),
            new OpenApiGeneratorRFC8040(schemaService, restconf));
    }

    @VisibleForTesting
    OpenApiServiceImpl(final MountPointOpenApiGeneratorRFC8040 mountPointOpenApiGeneratorRFC8040,
                       final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040) {
        mountPointOpenApiRFC8040 = requireNonNull(mountPointOpenApiGeneratorRFC8040).getMountPointOpenApi();
        this.openApiGeneratorRFC8040 = requireNonNull(openApiGeneratorRFC8040);
    }

    @Override
    public Response getAllModulesDoc(final UriInfo uriInfo, final @Nullable Integer width,
            final @Nullable Integer depth, final @Nullable Integer offset, final @Nullable Integer limit)
            throws IOException {
        final OpenApiInputStream stream = openApiGeneratorRFC8040.getControllerModulesDoc(uriInfo,
            requireNonNullElse(width, 0), requireNonNullElse(depth, 0), requireNonNullElse(offset, 0),
            requireNonNullElse(limit, 0));
        return Response.ok(stream).build();
    }

    @Override
    public Response getAllModulesMeta(final @Nullable Integer offset, final @Nullable Integer limit)
            throws IOException {
        final var metaStream = openApiGeneratorRFC8040.getControllerModulesMeta(requireNonNullElse(offset, 0),
            requireNonNullElse(limit, 0));
        return Response.ok(metaStream).build();
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public Response getDocByModule(final String module, final String revision, final UriInfo uriInfo,
            final @Nullable Integer width, final @Nullable Integer depth) throws IOException {
        final OpenApiInputStream stream = openApiGeneratorRFC8040.getApiDeclaration(module, revision, uriInfo,
            requireNonNullElse(width, 0), requireNonNullElse(depth, 0));
        return Response.ok(stream).build();
    }

    /**
     * Redirects to embedded swagger ui.
     */
    @Override
    public Response getApiExplorer(final UriInfo uriInfo) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("../../explorer/index.html").build()).build();
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
            final String revision, final UriInfo uriInfo, final @Nullable Integer width, final @Nullable Integer depth)
            throws IOException {
        final OpenApiInputStream stream =
            mountPointOpenApiRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum), module, revision,
                requireNonNullElse(width, 0), requireNonNullElse(depth, 0));
        return Response.ok(stream).build();
    }

    @Override
    public Response getMountDoc(final String instanceNum, final UriInfo uriInfo, final @Nullable Integer width,
            final @Nullable Integer depth, final @Nullable Integer offset, final @Nullable Integer limit)
            throws IOException {
        final OpenApiInputStream stream =
            mountPointOpenApiRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum),
                requireNonNullElse(width, 0), requireNonNullElse(depth, 0), requireNonNullElse(offset, 0),
                requireNonNullElse(limit, 0));
        return Response.ok(stream).build();
    }

    @Override
    public Response getMountMeta(final String instanceNum, final @Nullable Integer offset,
            final @Nullable Integer limit) throws IOException {
        final var metaStream = mountPointOpenApiRFC8040.getMountPointApiMeta(Long.parseLong(instanceNum),
                requireNonNullElse(offset, 0), requireNonNullElse(limit, 0));
        return Response.ok(metaStream).build();
    }
}
