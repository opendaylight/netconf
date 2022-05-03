/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.CommonApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.MountPointInstance;


/**
 * This service generates swagger (See
 * <a href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 *
 * <p>
 * NOTE: These API's need to be synchronized due to bug 1198. Thread access to
 * the SchemaContext is not synchronized properly and thus you can end up with
 * missing definitions without this synchronization. There are likely otherways
 * to work around this limitation, but given that this API is a dev only tool
 * and not dependent UI, this was the fastest work around.
 */
public class ApiDocServiceImpl implements ApiDocService {

    public static final int DEFAULT_PAGESIZE = 20;
    // Query parameter
    private static final String PAGE_NUM = "pageNum";

    public enum OAversion { V2_0, V3_0 }

    private final MountPointSwagger mountPointSwaggerRFC8040;
    private final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040;
    private final AllModulesDocGenerator allModulesDocGenerator;

    public ApiDocServiceImpl(final MountPointSwaggerGeneratorRFC8040 mountPointSwaggerGeneratorRFC8040,
                             final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040,
                             final AllModulesDocGenerator allModulesDocGenerator) {
        mountPointSwaggerRFC8040 =
                requireNonNull(mountPointSwaggerGeneratorRFC8040).getMountPointSwagger();
        this.apiDocGeneratorRFC8040 = requireNonNull(apiDocGeneratorRFC8040);
        this.allModulesDocGenerator = requireNonNull(allModulesDocGenerator);
    }

    @Override
    public synchronized Response getAllModulesDoc(final UriInfo uriInfo) {
        return Response.ok(allModulesDocGenerator.getAllModulesDoc(uriInfo, identifyOpenApiVersion(uriInfo))).build();
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public synchronized Response getDocByModule(final String module, final String revision, final UriInfo uriInfo) {
        return Response.ok(
            apiDocGeneratorRFC8040.getApiDeclaration(module, revision, uriInfo, identifyOpenApiVersion(uriInfo)))
            .build();
    }

    /**
     * Redirects to embedded swagger ui.
     */
    @Override
    public synchronized Response getApiExplorer(final UriInfo uriInfo) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("../explorer/index.html").build()).build();
    }

    @Override
    public synchronized Response getListOfMounts(final UriInfo uriInfo) {
        final List<MountPointInstance> entity = mountPointSwaggerRFC8040
                .getInstanceIdentifiers().entrySet().stream()
                .map(MountPointInstance::new).collect(Collectors.toList());
        return Response.ok(entity).build();
    }

    @Override
    public synchronized Response getMountDocByModule(final String instanceNum, final String module,
                                                     final String revision, final UriInfo uriInfo) {
        final OAversion oaversion = identifyOpenApiVersion(uriInfo);
        final CommonApiObject api = mountPointSwaggerRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum),
            module, revision, oaversion);
        return Response.ok(api).build();
    }

    @Override
    public synchronized Response getMountDoc(final String instanceNum, final UriInfo uriInfo) {
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
