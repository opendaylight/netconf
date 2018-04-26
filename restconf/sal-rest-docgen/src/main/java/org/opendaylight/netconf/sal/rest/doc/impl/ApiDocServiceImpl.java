/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.Objects;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.ApiDeclaration;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;

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

    private final MountPointSwagger mountPointSwaggerDraft02;
    private final MountPointSwagger mountPointSwaggerRFC8040;
    private final ApiDocGeneratorDraftO2 apiDocGeneratorDraft02;
    private final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040;

    public ApiDocServiceImpl(MountPointSwaggerGeneratorDraft02 mountPointSwaggerGeneratorDraft02,
            MountPointSwaggerGeneratorRFC8040 mountPointSwaggerGeneratorRFC8040,
            ApiDocGeneratorDraftO2 apiDocGeneratorDraft02, ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040) {
        this.mountPointSwaggerDraft02 =
                Objects.requireNonNull(mountPointSwaggerGeneratorDraft02).getMountPointSwagger();
        this.mountPointSwaggerRFC8040 =
                Objects.requireNonNull(mountPointSwaggerGeneratorRFC8040).getMountPointSwagger();
        this.apiDocGeneratorDraft02 = Objects.requireNonNull(apiDocGeneratorDraft02);
        this.apiDocGeneratorRFC8040 = Objects.requireNonNull(apiDocGeneratorRFC8040);
    }

    /**
     * Generates index document for Swagger UI. This document lists out all
     * modules with link to get APIs for each module. The API for each module is
     * served by <code> getDocByModule()</code> method.
     */
    @Override
    public synchronized Response getRootDoc(final UriInfo uriInfo) {
        final ResourceList rootDoc;
        if (isNew(uriInfo)) {
            rootDoc = apiDocGeneratorRFC8040.getResourceListing(uriInfo);
        } else {
            rootDoc = apiDocGeneratorDraft02.getResourceListing(uriInfo);
        }

        return Response.ok(rootDoc).build();
    }

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @Override
    public synchronized Response getDocByModule(final String module, final String revision, final UriInfo uriInfo) {
        final ApiDeclaration doc;
        if (isNew(uriInfo)) {
            doc = apiDocGeneratorRFC8040.getApiDeclaration(module, revision, uriInfo);
        } else {
            doc = apiDocGeneratorDraft02.getApiDeclaration(module, revision, uriInfo);
        }

        return Response.ok(doc).build();
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
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter streamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            JsonGenerator writer = new JsonFactory().createGenerator(streamWriter);
            writer.writeStartArray();
            for (final Entry<String, Long> entry : mountPointSwaggerDraft02.getInstanceIdentifiers()
                    .entrySet()) {
                writer.writeStartObject();
                writer.writeObjectField("instance", entry.getKey());
                writer.writeObjectField("id", entry.getValue());
                writer.writeEndObject();
            }
            writer.writeEndArray();
            writer.flush();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }

        try {
            String responseStr = baos.toString(StandardCharsets.UTF_8.name());
            return Response.status(Response.Status.OK).entity(responseStr).build();
        } catch (UnsupportedEncodingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @Override
    public synchronized Response getMountRootDoc(final String instanceNum, final UriInfo uriInfo) {
        final ResourceList resourceList;
        if (isNew(uriInfo)) {
            resourceList = mountPointSwaggerRFC8040.getResourceList(uriInfo, Long.parseLong(instanceNum));
        } else {
            resourceList = mountPointSwaggerDraft02.getResourceList(uriInfo, Long.parseLong(instanceNum));
        }
        return Response.ok(resourceList).build();
    }

    @Override
    public synchronized Response getMountDocByModule(final String instanceNum, final String module,
            final String revision, final UriInfo uriInfo) {
        final ApiDeclaration api;
        if (isNew(uriInfo)) {
            api = mountPointSwaggerRFC8040.getMountPointApi(uriInfo, Long.parseLong(instanceNum), module, revision);
        } else {
            api = mountPointSwaggerDraft02.getMountPointApi(uriInfo, Long.parseLong(instanceNum), module, revision);
        }
        return Response.ok(api).build();
    }

    private static boolean isNew(final UriInfo uriInfo) {
        return uriInfo.getBaseUri().toString().contains("/18/");
    }
}
