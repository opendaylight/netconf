/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.ChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * The "{+restconf}/data" subtree represents the datastore resource type, which is a collection of configuration data
 * and state data nodes.
 */
@Path("/")
public final class RestconfDataServiceImpl {
    private final DatabindProvider databindProvider;
    private final MdsalRestconfServer server;

    public RestconfDataServiceImpl(final DatabindProvider databindProvider, final MdsalRestconfServer server) {
        this.databindProvider = requireNonNull(databindProvider);
        this.server = requireNonNull(server);
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(final InputStream body, @Context final UriInfo uriInfo,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonChildBody(body)) {
            postData(jsonBody, uriInfo, ar);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final var reqPath = server.bindRequestPath(identifier);
        if (reqPath.getSchemaNode() instanceof ActionDefinition) {
            try (var jsonBody = new JsonOperationInputBody(body)) {
                invokeAction(reqPath, jsonBody, ar);
            }
        } else {
            try (var jsonBody = new JsonChildBody(body)) {
                postData(reqPath.inference(), reqPath.getInstanceIdentifier(), jsonBody, uriInfo,
                    reqPath.getMountPoint(), ar);
            }
        }
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void postDataXML(final InputStream body, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlChildBody(body)) {
            postData(xmlBody, uriInfo, ar);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void postDataXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final var reqPath = server.bindRequestPath(identifier);
        if (reqPath.getSchemaNode() instanceof ActionDefinition) {
            try (var xmlBody = new XmlOperationInputBody(body)) {
                invokeAction(reqPath, xmlBody, ar);
            }
        } else {
            try (var xmlBody = new XmlChildBody(body)) {
                postData(reqPath.inference(), reqPath.getInstanceIdentifier(), xmlBody, uriInfo,
                    reqPath.getMountPoint(), ar);
            }
        }
    }

    private void postData(final ChildBody body, final UriInfo uriInfo, final AsyncResponse ar) {
        postData(Inference.ofDataTreePath(databindProvider.currentContext().modelContext()),
            YangInstanceIdentifier.of(), body, uriInfo, null, ar);
    }

    private void postData(final Inference inference, final YangInstanceIdentifier parentPath, final ChildBody body,
            final UriInfo uriInfo, final @Nullable DOMMountPoint mountPoint, final AsyncResponse ar) {
        final var modelContext = inference.getEffectiveModelContext();
        final var insert = QueryParams.parseInsert(modelContext, uriInfo);
        final var strategy = server.getRestconfStrategy(modelContext, mountPoint);
        final var payload = body.toPayload(parentPath, inference);
        final var data = payload.body();
        final var path = concat(parentPath, payload.prefix());

        strategy.postData(path, data, insert).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final CreateResource result) {
                return Response.created(uriInfo.getBaseUriBuilder().path("data").path(result.createdPath()).build())
                    .build();
            }
        });
    }

    private static YangInstanceIdentifier concat(final YangInstanceIdentifier parent, final List<PathArgument> args) {
        var ret = parent;
        for (var arg : args) {
            ret = ret.node(arg);
        }
        return ret;
    }

    /**
     * Invoke Action operation.
     *
     * @param payload {@link NormalizedNodePayload} - the body of the operation
     * @param ar {@link AsyncResponse} which needs to be completed with a NormalizedNodePayload
     */
    private void invokeAction(final InstanceIdentifierContext reqPath, final OperationInputBody body,
            final AsyncResponse ar) {
        server.dataInvokePOST(reqPath, body).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DOMActionResult result) {
                final var output = result.getOutput().orElse(null);
                return output == null || output.isEmpty() ? Response.status(Status.NO_CONTENT).build()
                    : Response.status(Status.OK).entity(new NormalizedNodePayload(reqPath.inference(), output)).build();
            }
        });
    }
}
