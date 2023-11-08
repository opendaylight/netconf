/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionException;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.ChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.CreateOrReplaceResult;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "{+restconf}/data" subtree represents the datastore resource type, which is a collection of configuration data
 * and state data nodes.
 */
@Path("/")
public final class RestconfDataServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataServiceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final DatabindProvider databindProvider;
    private final DOMActionService actionService;
    private final MdsalRestconfServer server;

    public RestconfDataServiceImpl(final DatabindProvider databindProvider, final MdsalRestconfServer server,
            final DOMActionService actionService) {
        this.databindProvider = requireNonNull(databindProvider);
        this.server = requireNonNull(server);
        this.actionService = requireNonNull(actionService);
    }

    /**
     * Get target data resource from data root.
     *
     * @param uriInfo URI info
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/data")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response readData(@Context final UriInfo uriInfo) {
        final var readParams = QueryParams.newReadDataParams(uriInfo);
        return readData(server.bindRequestRoot(), readParams);
    }

    /**
     * Get target data resource.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/data/{identifier:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response readData(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo) {
        final var readParams = QueryParams.newReadDataParams(uriInfo);
        return readData(server.bindRequestPath(identifier), readParams);
    }

    private Response readData(final InstanceIdentifierContext reqPath, final ReadDataParams readParams) {
        final var queryParams = QueryParams.newQueryParameters(readParams, reqPath);
        final var fieldPaths = queryParams.fieldPaths();
        final var strategy = server.getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint());
        final NormalizedNode node;
        if (fieldPaths != null && !fieldPaths.isEmpty()) {
            node = strategy.readData(readParams.content(), reqPath.getInstanceIdentifier(),
                readParams.withDefaults(), fieldPaths);
        } else {
            node = strategy.readData(readParams.content(), reqPath.getInstanceIdentifier(),
                readParams.withDefaults());
        }
        if (node == null) {
            throw new RestconfDocumentedException(
                "Request could not be completed because the relevant data model content does not exist",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        }

        return switch (readParams.content()) {
            case ALL, CONFIG -> {
                final QName type = node.name().getNodeType();
                yield Response.status(Status.OK)
                    .entity(new NormalizedNodePayload(reqPath.inference(), node, queryParams))
                    .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null) + "-"
                        + type.getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
            }
            case NONCONFIG -> Response.status(Status.OK)
                .entity(new NormalizedNodePayload(reqPath.inference(), node, queryParams))
                .build();
        };
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void putDataJSON(@Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            putData(null, uriInfo, jsonBody, ar);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void putDataJSON(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            putData(identifier, uriInfo, jsonBody, ar);
        }
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void putDataXML(@Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            putData(null, uriInfo, xmlBody, ar);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void putDataXML(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            putData(identifier, uriInfo, xmlBody, ar);
        }
    }

    private void putData(final @Nullable String identifier, final UriInfo uriInfo, final ResourceBody body,
            final AsyncResponse ar) {
        final var reqPath = server.bindRequestPath(identifier);
        final var insert = QueryParams.parseInsert(reqPath.getSchemaContext(), uriInfo);
        final var req = bindResourceRequest(reqPath, body);

        req.strategy().putData(req.path(), req.data(), insert).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final CreateOrReplaceResult result) {
                return switch (result) {
                    // Note: no Location header, as it matches the request path
                    case CREATED -> Response.status(Status.CREATED).build();
                    case REPLACED -> Response.noContent().build();
                };
            }
        });
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
            Response transform(final Empty result) {
                return Response.created(resolveLocation(uriInfo, path, modelContext, data)).build();
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
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}.
     *
     * @param uriInfo       uri info
     * @param initialPath   data path
     * @param schemaContext reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final YangInstanceIdentifier initialPath,
                                       final EffectiveModelContext schemaContext, final NormalizedNode data) {
        YangInstanceIdentifier path = initialPath;
        if (data instanceof MapNode mapData) {
            final var children = mapData.body();
            if (!children.isEmpty()) {
                path = path.node(children.iterator().next().name());
            }
        }

        return uriInfo.getBaseUriBuilder().path("data").path(IdentifierCodec.serialize(path, schemaContext)).build();
    }

    /**
     * Delete the target data resource.
     *
     * @param identifier path to target
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @DELETE
    @Path("/data/{identifier:.+}")
    public void deleteData(@Encoded @PathParam("identifier") final String identifier,
            @Suspended final AsyncResponse ar) {
        final var reqPath = server.bindRequestPath(identifier);
        final var strategy = server.getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint());

        strategy.delete(reqPath.getInstanceIdentifier()).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final Empty result) {
                return Response.noContent().build();
            }
        });
    }

    /**
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void plainPatchDataXML(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            plainPatchData(xmlBody, ar);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void plainPatchDataXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            plainPatchData(identifier, xmlBody, ar);
        }
    }

    /**
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void plainPatchDataJSON(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            plainPatchData(jsonBody, ar);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void plainPatchDataJSON(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            plainPatchData(identifier, jsonBody, ar);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    private void plainPatchData(final ResourceBody body, final AsyncResponse ar) {
        plainPatchData(server.bindRequestRoot(), body, ar);
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    private void plainPatchData(final String identifier, final ResourceBody body, final AsyncResponse ar) {
        plainPatchData(server.bindRequestPath(identifier), body, ar);
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param reqPath path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    private void plainPatchData(final InstanceIdentifierContext reqPath, final ResourceBody body,
            final AsyncResponse ar) {
        final var req = bindResourceRequest(reqPath, body);
        req.strategy().merge(req.path(), req.data()).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final Empty result) {
                return Response.ok().build();
            }
        });
    }

    private @NonNull ResourceRequest bindResourceRequest(final InstanceIdentifierContext reqPath,
            final ResourceBody body) {
        final var inference = reqPath.inference();
        final var path = reqPath.getInstanceIdentifier();
        final var data = body.toNormalizedNode(path, inference, reqPath.getSchemaNode());

        return new ResourceRequest(
            server.getRestconfStrategy(inference.getEffectiveModelContext(), reqPath.getMountPoint()),
            path, data);
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void yangPatchDataXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            yangPatchData(identifier, xmlBody, ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void yangPatchDataXML(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            yangPatchData(xmlBody, ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void yangPatchDataJSON(@Encoded @PathParam("identifier") final String identifier,
            final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            yangPatchData(identifier, jsonBody, ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void yangPatchDataJSON(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            yangPatchData(jsonBody, ar);
        }
    }

    private void yangPatchData(final @NonNull PatchBody body, final AsyncResponse ar) {
        final var context = server.bindRequestRoot().getSchemaContext();
        yangPatchData(context, parsePatchBody(context, YangInstanceIdentifier.of(), body), null, ar);
    }

    private void yangPatchData(final String identifier, final @NonNull PatchBody body,
            final AsyncResponse ar) {
        final var reqPath = server.bindRequestPath(identifier);
        final var modelContext = reqPath.getSchemaContext();
        yangPatchData(modelContext, parsePatchBody(modelContext, reqPath.getInstanceIdentifier(), body),
            reqPath.getMountPoint(), ar);
    }

    @VisibleForTesting
    void yangPatchData(final @NonNull EffectiveModelContext modelContext,
            final @NonNull PatchContext patch, final @Nullable DOMMountPoint mountPoint, final AsyncResponse ar) {
        server.getRestconfStrategy(modelContext, mountPoint).patchData(patch)
            .addCallback(new JaxRsRestconfCallback<>(ar) {
                @Override
                Response transform(final PatchStatusContext result) {
                    return Response.status(getStatusCode(result)).entity(result).build();
                }
            });
    }

    private static Status getStatusCode(final PatchStatusContext result) {
        if (result.ok()) {
            return Status.OK;
        } else if (result.globalErrors() == null || result.globalErrors().isEmpty()) {
            return result.editCollection().stream()
                .filter(patchStatus -> !patchStatus.isOk() && !patchStatus.getEditErrors().isEmpty())
                .findFirst()
                .map(PatchStatusEntity::getEditErrors)
                .flatMap(errors -> errors.stream().findFirst())
                .map(error -> ErrorTags.statusOf(error.getErrorTag()))
                .orElse(Status.INTERNAL_SERVER_ERROR);
        } else {
            final var error = result.globalErrors().iterator().next();
            return ErrorTags.statusOf(error.getErrorTag());
        }
    }

    private static @NonNull PatchContext parsePatchBody(final @NonNull EffectiveModelContext context,
            final @NonNull YangInstanceIdentifier urlPath, final @NonNull PatchBody body) {
        try {
            return body.toPatchContext(context, urlPath);
        } catch (IOException e) {
            LOG.debug("Error parsing YANG Patch input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    /**
     * Invoke Action operation.
     *
     * @param payload {@link NormalizedNodePayload} - the body of the operation
     * @param ar {@link AsyncResponse} which needs to be completed with a NormalizedNodePayload
     */
    private void invokeAction(final InstanceIdentifierContext reqPath, final OperationInputBody body,
            final AsyncResponse ar) {
        final var yangIIdContext = reqPath.getInstanceIdentifier();
        final ContainerNode input;
        try {
            input = body.toContainerNode(reqPath.inference());
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }

        final var mountPoint = reqPath.getMountPoint();
        final var inference = reqPath.inference();
        final var schemaPath = inference.toSchemaInferenceStack().toSchemaNodeIdentifier();
        final var response = mountPoint != null ? invokeAction(input, schemaPath, yangIIdContext, mountPoint)
            : invokeAction(input, schemaPath, yangIIdContext, actionService);

        response.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DOMActionResult result) {
                final var output = result.getOutput().orElse(null);
                return output == null || output.isEmpty() ? Response.status(Status.NO_CONTENT).build()
                    : Response.status(Status.OK).entity(new NormalizedNodePayload(inference, output)).build();
            }
        });
    }

    /**
     * Invoking Action via mount point.
     *
     * @param mountPoint mount point
     * @param data input data
     * @param schemaPath schema path of data
     * @return {@link DOMActionResult}
     */
    private static RestconfFuture<DOMActionResult> invokeAction(final ContainerNode data,
            final Absolute schemaPath, final YangInstanceIdentifier yangIId, final DOMMountPoint mountPoint) {
        final var actionService = mountPoint.getService(DOMActionService.class);
        return actionService.isPresent() ? invokeAction(data, schemaPath, yangIId, actionService.orElseThrow())
            : RestconfFuture.failed(new RestconfDocumentedException("DOMActionService is missing."));
    }

    /**
     * Invoke Action via ActionServiceHandler.
     *
     * @param data input data
     * @param yangIId invocation context
     * @param schemaPath schema path of data
     * @param actionService action service to invoke action
     * @return {@link DOMActionResult}
     */
    private static RestconfFuture<DOMActionResult> invokeAction(final ContainerNode data, final Absolute schemaPath,
            final YangInstanceIdentifier yangIId, final DOMActionService actionService) {
        final var ret = new SettableRestconfFuture<DOMActionResult>();

        Futures.addCallback(actionService.invokeAction(schemaPath,
            new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, yangIId.getParent()), data),
            new FutureCallback<DOMActionResult>() {
                @Override
                public void onSuccess(final DOMActionResult result) {
                    final var errors = result.getErrors();
                    LOG.debug("InvokeAction Error Message {}", errors);
                    if (errors.isEmpty()) {
                        ret.set(result);
                    } else {
                        ret.setFailure(new RestconfDocumentedException("InvokeAction Error Message ", null, errors));
                    }
                }

                @Override
                public void onFailure(final Throwable cause) {
                    if (cause instanceof DOMActionException) {
                        ret.set(new SimpleDOMActionResult(List.of(RpcResultBuilder.newError(
                            ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause.getMessage()))));
                    } else if (cause instanceof RestconfDocumentedException e) {
                        ret.setFailure(e);
                    } else if (cause instanceof CancellationException) {
                        ret.setFailure(new RestconfDocumentedException("Action cancelled while executing",
                            ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, cause));
                    } else {
                        ret.setFailure(new RestconfDocumentedException("Invocation failed", cause));
                    }
                }
            }, MoreExecutors.directExecutor());

        return ret;
    }
}
