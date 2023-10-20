/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.CommonApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.MountPointInstance;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
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
public final class ApiDocServiceImpl implements ApiDocService {
    // FIXME: make this configurable
    public static final int DEFAULT_PAGESIZE = 20;

    // Query parameter
    private static final String PAGE_NUM = "pageNum";

    public enum OAversion { V2_0, V3_0 }

    private final MountPointSwagger mountPointSwaggerRFC8040;
    private final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040;

    @Inject
    @Activate
    public ApiDocServiceImpl(final @Reference DOMSchemaService schemaService,
                             final @Reference DOMMountPointService mountPointService) {
        this(new MountPointSwaggerGeneratorRFC8040(schemaService, mountPointService),
            new ApiDocGeneratorRFC8040(schemaService));
    }

    public ApiDocServiceImpl(final DOMSchemaService schemaService,
                             final DOMMountPointService mountPointService,
                             final String basePath) {
        this(new MountPointSwaggerGeneratorRFC8040(schemaService, mountPointService, basePath),
            new ApiDocGeneratorRFC8040(schemaService, basePath));
    }

    @VisibleForTesting
    ApiDocServiceImpl(final MountPointSwaggerGeneratorRFC8040 mountPointSwaggerGeneratorRFC8040,
                      final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040) {
        mountPointSwaggerRFC8040 = requireNonNull(mountPointSwaggerGeneratorRFC8040).getMountPointSwagger();
        this.apiDocGeneratorRFC8040 = requireNonNull(apiDocGeneratorRFC8040);
    }

    @Override
    public Response getAllModulesDoc(final UriInfo uriInfo) {
        final OAversion oaversion = identifyOpenApiVersion(uriInfo);
        final DefinitionNames definitionNames = new DefinitionNames();
        final SwaggerObject doc = apiDocGeneratorRFC8040.getAllModulesDoc(uriInfo, definitionNames, oaversion);

        return Response.ok(BaseYangSwaggerGenerator.getAppropriateDoc(doc, oaversion)).build();
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public Response getDocByModule(final String module, final String revision, final UriInfo uriInfo) {
        return Response.ok(
            apiDocGeneratorRFC8040.getApiDeclaration(module, revision, uriInfo, identifyOpenApiVersion(uriInfo)))
            .build();
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
        final List<MountPointInstance> entity = mountPointSwaggerRFC8040
                .getInstanceIdentifiers().entrySet().stream()
                .map(MountPointInstance::new).collect(Collectors.toList());
        return Response.ok(entity).build();
    }

    @Override
    public Response getMountDocByModule(final String instanceNum, final String module,
                                                     final String revision, final UriInfo uriInfo) {
        final OAversion oaversion = identifyOpenApiVersion(uriInfo);
        final CommonApiObject api = mountPointSwaggerRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum),
            module, revision, oaversion);
        return Response.ok(api).build();
    }

    @Override
    public Response getMountDoc(final String instanceNum, final UriInfo uriInfo) {
        final CommonApiObject api;
        final OAversion oaversion = identifyOpenApiVersion(uriInfo);
        final String stringPageNum = uriInfo.getQueryParameters().getFirst(PAGE_NUM);
        final Optional<Integer> pageNum = stringPageNum != null ? Optional.of(Integer.valueOf(stringPageNum))
                : Optional.empty();
        api = mountPointSwaggerRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum), pageNum, oaversion);
        return Response.ok(api).build();
    }

    private static OAversion identifyOpenApiVersion(final UriInfo uriInfo) {
        if (uriInfo.getBaseUri().toString().contains("/swagger2/")) {
            return OAversion.V2_0;
        }
        return OAversion.V3_0;
    }
}
