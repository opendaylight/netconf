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
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.MountPointsEntity;
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
@Singleton
@Component(service = OpenApiService.class)
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

    // FIXME Public for end-to-end testing openapi over Netty. Constructor that uses NettyEndpoint should be used
    //  instead when we get one.
    @VisibleForTesting
    public OpenApiServiceImpl(final MountPointOpenApiGeneratorRFC8040 mountPointOpenApiGeneratorRFC8040,
                       final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040) {
        mountPointOpenApiRFC8040 = requireNonNull(mountPointOpenApiGeneratorRFC8040).getMountPointOpenApi();
        this.openApiGeneratorRFC8040 = requireNonNull(openApiGeneratorRFC8040);
    }

    @Override
    public DocumentEntity getAllModulesDoc(final URI uri, final @Nullable Integer width, final @Nullable Integer depth,
            final @Nullable Integer offset, final @Nullable Integer limit) throws IOException {
        return openApiGeneratorRFC8040.getControllerModulesDoc(uri, unboxOrZero(width), unboxOrZero(depth),
            unboxOrZero(offset), unboxOrZero(limit));
    }

    @Override
    public MetadataEntity getAllModulesMeta(final @Nullable Integer offset, final @Nullable Integer limit)
            throws IOException {
        return openApiGeneratorRFC8040.getControllerModulesMeta(unboxOrZero(offset), unboxOrZero(limit));
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public DocumentEntity getDocByModule(final String module, final String revision, final URI uri,
            final @Nullable Integer width, final @Nullable Integer depth) throws IOException {
        return openApiGeneratorRFC8040.getApiDeclaration(module, revision, uri, unboxOrZero(width), unboxOrZero(depth));
    }

    @Override
    public MountPointsEntity getListOfMounts() {
        return mountPointOpenApiRFC8040.getInstanceIdentifiers();
    }

    @Override
    public DocumentEntity getMountDocByModule(final long instanceNum, final String module, final String revision,
            final URI uri, final @Nullable Integer width, final @Nullable Integer depth) throws IOException {
        return mountPointOpenApiRFC8040.getMountPointApi(uri, instanceNum, module, revision, unboxOrZero(width),
            unboxOrZero(depth));
    }

    @Override
    public DocumentEntity getMountDoc(final long instanceNum, final URI uri, final @Nullable Integer width,
            final @Nullable Integer depth, final @Nullable Integer offset, final @Nullable Integer limit)
            throws IOException {
        return mountPointOpenApiRFC8040.getMountPointApi(uri, instanceNum, unboxOrZero(width), unboxOrZero(depth),
            unboxOrZero(offset), unboxOrZero(limit));
    }

    @Override
    public MetadataEntity getMountMeta(final long instanceNum, final @Nullable Integer offset,
            final @Nullable Integer limit) throws IOException {
        return mountPointOpenApiRFC8040.getMountPointApiMeta(instanceNum, unboxOrZero(offset), unboxOrZero(limit));
    }

    private static int unboxOrZero(final @Nullable Integer value) {
        return value != null ? value : 0;
    }
}
