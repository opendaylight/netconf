/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.NOTIFICATION_STREAM;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAMS_PATH;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_ACCESS_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_LOCATION_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_PATH;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_PATH_PART;

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
import java.util.concurrent.ExecutionException;
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
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
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
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.monitoring.RestconfStateStreams;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
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

    private final RestconfStreamsSubscriptionService delegRestconfSubscrService;
    private final DatabindProvider databindProvider;
    private final MdsalRestconfStrategy restconfStrategy;
    private final DOMMountPointService mountPointService;
    private final SubscribeToStreamUtil streamUtils;
    private final DOMActionService actionService;
    private final DOMDataBroker dataBroker;
    private final ListenersBroker listenersBroker;

    public RestconfDataServiceImpl(final DatabindProvider databindProvider,
            final DOMDataBroker dataBroker, final DOMMountPointService  mountPointService,
            final RestconfStreamsSubscriptionService delegRestconfSubscrService,
            final DOMActionService actionService, final StreamsConfiguration configuration,
            final ListenersBroker listenersBroker) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        restconfStrategy = new MdsalRestconfStrategy(dataBroker);
        this.mountPointService = requireNonNull(mountPointService);
        this.delegRestconfSubscrService = requireNonNull(delegRestconfSubscrService);
        this.actionService = requireNonNull(actionService);
        streamUtils = configuration.useSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
        this.listenersBroker = listenersBroker;
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
        return readData(null, uriInfo);
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
        final ReadDataParams readParams = QueryParams.newReadDataParams(uriInfo);

        final EffectiveModelContext schemaContextRef = databindProvider.currentContext().modelContext();
        // FIXME: go through
        final InstanceIdentifierContext instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef, mountPointService);
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();

        // FIXME: this looks quite crazy, why do we even have it?
        if (mountPoint == null && identifier != null && identifier.contains(STREAMS_PATH)
            && !identifier.contains(STREAM_PATH_PART)) {
            createAllYangNotificationStreams(schemaContextRef, uriInfo);
        }

        final QueryParameters queryParams = QueryParams.newQueryParameters(readParams, instanceIdentifier);
        final List<YangInstanceIdentifier> fieldPaths = queryParams.fieldPaths();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        final NormalizedNode node;
        if (fieldPaths != null && !fieldPaths.isEmpty()) {
            node = ReadDataTransactionUtil.readData(readParams.content(), instanceIdentifier.getInstanceIdentifier(),
                    strategy, readParams.withDefaults(), schemaContextRef, fieldPaths);
        } else {
            node = ReadDataTransactionUtil.readData(readParams.content(), instanceIdentifier.getInstanceIdentifier(),
                    strategy, readParams.withDefaults(), schemaContextRef);
        }

        // FIXME: this is utter craziness, refactor it properly!
        if (identifier != null && identifier.contains(STREAM_PATH) && identifier.contains(STREAM_ACCESS_PATH_PART)
                && identifier.contains(STREAM_LOCATION_PATH_PART)) {
            final String value = (String) node.body();
            final String streamName = value.substring(value.indexOf(NOTIFICATION_STREAM + '/'));
            delegRestconfSubscrService.subscribeToStream(streamName, uriInfo);
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
                    .entity(NormalizedNodePayload.ofReadData(instanceIdentifier, node, queryParams))
                    .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null) + "-"
                        + type.getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
            }
            case NONCONFIG -> Response.status(Status.OK)
                .entity(NormalizedNodePayload.ofReadData(instanceIdentifier, node, queryParams))
                .build();
        };
    }

    private void createAllYangNotificationStreams(final EffectiveModelContext schemaContext, final UriInfo uriInfo) {
        final var transaction = dataBroker.newWriteOnlyTransaction();

        for (var module : schemaContext.getModuleStatements().values()) {
            final var moduleName = module.argument().getLocalName();
            // Note: this handles only RFC6020 notifications
            module.streamEffectiveSubstatements(NotificationEffectiveStatement.class).forEach(notification -> {
                final var notifName = notification.argument();

                writeNotificationStreamToDatastore(schemaContext, uriInfo, transaction,
                    createYangNotifiStream(listenersBroker, moduleName, notifName, NotificationOutputType.XML));
                writeNotificationStreamToDatastore(schemaContext, uriInfo, transaction,
                    createYangNotifiStream(listenersBroker, moduleName, notifName, NotificationOutputType.JSON));
            });
        }

        try {
            transaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    private static NotificationListenerAdapter createYangNotifiStream(final ListenersBroker listenersBroker,
            final String moduleName, final QName notifName, final NotificationOutputType outputType) {
        final var streamName = createNotificationStreamName(moduleName, notifName.getLocalName(), outputType);

        final var existing = listenersBroker.notificationListenerFor(streamName);
        return existing != null ? existing
            : listenersBroker.registerNotificationListener(Absolute.of(notifName), streamName, outputType);
    }

    private static String createNotificationStreamName(final String moduleName, final String notifName,
            final NotificationOutputType outputType) {
        final var sb = new StringBuilder()
            .append(RestconfStreamsConstants.NOTIFICATION_STREAM)
            .append('/').append(moduleName).append(':').append(notifName);
        if (outputType != NotificationOutputType.XML) {
            sb.append('/').append(outputType.getName());
        }
        return sb.toString();
    }

    private void writeNotificationStreamToDatastore(final EffectiveModelContext schemaContext,
            final UriInfo uriInfo, final DOMDataTreeWriteOperations tx, final NotificationListenerAdapter listener) {
        final URI uri = streamUtils.prepareUriByStreamName(uriInfo, listener.getStreamName());
        final MapEntryNode mapToStreams = RestconfStateStreams.notificationStreamEntry(schemaContext,
                listener.getSchemaPath().lastNodeIdentifier(), null, listener.getOutputType(), uri);

        tx.merge(LogicalDatastoreType.OPERATIONAL,
            RestconfStateStreams.restconfStateStreamPath(mapToStreams.name()), mapToStreams);
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public Response putDataJSON(@Context final UriInfo uriInfo, final InputStream body) {
        try (var jsonBody = new JsonResourceBody(body)) {
            return putData(null, uriInfo, jsonBody);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public Response putDataJSON(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body) {
        try (var jsonBody = new JsonResourceBody(body)) {
            return putData(identifier, uriInfo, jsonBody);
        }
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response putDataXML(@Context final UriInfo uriInfo, final InputStream body) {
        try (var xmlBody = new XmlResourceBody(body)) {
            return putData(null, uriInfo, xmlBody);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response putDataXML(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body) {
        try (var xmlBody = new XmlResourceBody(body)) {
            return putData(identifier, uriInfo, xmlBody);
        }
    }

    private Response putData(final @Nullable String identifier, final UriInfo uriInfo, final ResourceBody body) {
        final var insert = QueryParams.parseInsert(uriInfo);
        final var req = bindResourceRequest(identifier, body);

        return switch (
            req.strategy().putData(req.path(), req.data(), req.modelContext(), insert)) {
            // Note: no Location header, as it matches the request path
            case CREATED -> Response.status(Status.CREATED).build();
            case REPLACED -> Response.noContent().build();
        };
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public Response postDataJSON(final InputStream body, @Context final UriInfo uriInfo) {
        try (var jsonBody = new JsonChildBody(body)) {
            return postData(jsonBody, uriInfo);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public Response postDataJSON(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo) {
        final var instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), mountPointService);
        if (instanceIdentifier.getSchemaNode() instanceof ActionDefinition) {
            try (var jsonBody = new JsonOperationInputBody(body)) {
                return invokeAction(instanceIdentifier, jsonBody);
            }
        }

        try (var jsonBody = new JsonChildBody(body)) {
            return postData(instanceIdentifier, jsonBody, uriInfo);
        }
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response postDataXML(final InputStream body, @Context final UriInfo uriInfo) {
        try (var xmlBody = new XmlChildBody(body)) {
            return postData(xmlBody, uriInfo);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response postDataXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo) {
        final var instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), mountPointService);
        if (instanceIdentifier.getSchemaNode() instanceof ActionDefinition) {
            try (var xmlBody = new XmlOperationInputBody(body)) {
                return invokeAction(instanceIdentifier, xmlBody);
            }
        }

        try (var xmlBody = new XmlChildBody(body)) {
            return postData(instanceIdentifier, xmlBody, uriInfo);
        }
    }

    private Response postData(final ChildBody body, final UriInfo uriInfo) {
        return postData(InstanceIdentifierContext.ofLocalRoot(databindProvider.currentContext().modelContext()), body,
            uriInfo);
    }

    private Response postData(final InstanceIdentifierContext iid, final ChildBody body, final UriInfo uriInfo) {
        final var insert = QueryParams.parseInsert(uriInfo);
        final var strategy = getRestconfStrategy(iid.getMountPoint());
        final var context = iid.getSchemaContext();
        var path = iid.getInstanceIdentifier();
        final var payload = body.toPayload(path, iid.inference());
        final var data = payload.body();

        for (var arg : payload.prefix()) {
            path = path.node(arg);
        }

        strategy.postData(path, data, context, insert);
        return Response.created(resolveLocation(uriInfo, path, context, data)).build();
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
        final var instanceIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), mountPointService);
        final var strategy = getRestconfStrategy(instanceIdentifier.getMountPoint());

        Futures.addCallback(strategy.delete(instanceIdentifier.getInstanceIdentifier()), new FutureCallback<>() {
            @Override
            public void onSuccess(final Empty result) {
                ar.resume(Response.noContent().build());
            }

            @Override
            public void onFailure(final Throwable failure) {
                ar.resume(failure);
            }
        }, MoreExecutors.directExecutor());
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
            plainPatchData(null, xmlBody, ar);
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
            plainPatchData(null, jsonBody, ar);
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
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    private void plainPatchData(final @Nullable String identifier, final ResourceBody body, final AsyncResponse ar) {
        final var req = bindResourceRequest(identifier, body);
        final var future = req.strategy().merge(req.path(), req.data(), req.modelContext());

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(final Empty result) {
                ar.resume(Response.ok().build());
            }

            @Override
            public void onFailure(final Throwable failure) {
                ar.resume(failure);
            }
        }, MoreExecutors.directExecutor());
    }

    private @NonNull ResourceRequest bindResourceRequest(final @Nullable String identifier, final ResourceBody body) {
        final var dataBind = databindProvider.currentContext();
        final var context = ParserIdentifier.toInstanceIdentifier(identifier, dataBind.modelContext(),
            mountPointService);
        final var inference = context.inference();
        final var path = context.getInstanceIdentifier();
        final var data = body.toNormalizedNode(path, inference, context.getSchemaNode());

        return new ResourceRequest(getRestconfStrategy(context.getMountPoint()), inference.getEffectiveModelContext(),
            path, data);
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @return {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext yangPatchDataXML(@Encoded @PathParam("identifier") final String identifier,
            final InputStream body) {
        try (var xmlBody = new XmlPatchBody(body)) {
            return yangPatchData(identifier, xmlBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @return {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext yangPatchDataXML(final InputStream body) {
        try (var xmlBody = new XmlPatchBody(body)) {
            return yangPatchData(xmlBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @return {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext yangPatchDataJSON(@Encoded @PathParam("identifier") final String identifier,
            final InputStream body) {
        try (var jsonBody = new JsonPatchBody(body)) {
            return yangPatchData(identifier, jsonBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @return {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext yangPatchDataJSON(final InputStream body) {
        try (var jsonBody = new JsonPatchBody(body)) {
            return yangPatchData(jsonBody);
        }
    }

    private PatchStatusContext yangPatchData(final @NonNull PatchBody body) {
        return yangPatchData(InstanceIdentifierContext.ofLocalRoot(databindProvider.currentContext().modelContext()),
            body);
    }

    private PatchStatusContext yangPatchData(final String identifier, final @NonNull PatchBody body) {
        return yangPatchData(ParserIdentifier.toInstanceIdentifier(identifier,
                databindProvider.currentContext().modelContext(), mountPointService), body);
    }

    private PatchStatusContext yangPatchData(final @NonNull InstanceIdentifierContext targetResource,
            final @NonNull PatchBody body) {
        try {
            return yangPatchData(targetResource, body.toPatchContext(targetResource));
        } catch (IOException e) {
            LOG.debug("Error parsing YANG Patch input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    @VisibleForTesting
    PatchStatusContext yangPatchData(final InstanceIdentifierContext targetResource, final PatchContext context) {
        return PatchDataTransactionUtil.patchData(context, getRestconfStrategy(targetResource.getMountPoint()),
            targetResource.getSchemaContext());
    }

    @VisibleForTesting
    RestconfStrategy getRestconfStrategy(final DOMMountPoint mountPoint) {
        if (mountPoint == null) {
            return restconfStrategy;
        }

        return RestconfStrategy.forMountPoint(mountPoint).orElseThrow(() -> {
            LOG.warn("Mount point {} does not expose a suitable access interface", mountPoint.getIdentifier());
            return new RestconfDocumentedException("Could not find a supported access interface in mount point "
                + mountPoint.getIdentifier());
        });
    }

    /**
     * Invoke Action operation.
     *
     * @param payload {@link NormalizedNodePayload} - the body of the operation
     * @return {@link NormalizedNodePayload} wrapped in {@link Response}
     */
    private Response invokeAction(final InstanceIdentifierContext context, final OperationInputBody body) {
        final var yangIIdContext = context.getInstanceIdentifier();
        final ContainerNode input;
        try {
            input = body.toContainerNode(context.inference());
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }

        final var mountPoint = context.getMountPoint();
        final var schemaPath = context.inference().toSchemaInferenceStack().toSchemaNodeIdentifier();
        final var response = mountPoint != null ? invokeAction(input, schemaPath, yangIIdContext, mountPoint)
            : invokeAction(input, schemaPath, yangIIdContext, actionService);
        final var result = checkActionResponse(response);

        final var resultData = result != null ? result.getOutput().orElse(null) : null;
        if (resultData != null && resultData.isEmpty()) {
            return Response.status(Status.NO_CONTENT).build();
        }
        return Response.status(Status.OK).entity(NormalizedNodePayload.ofNullable(context, resultData)).build();
    }

    /**
     * Invoking Action via mount point.
     *
     * @param mountPoint mount point
     * @param data input data
     * @param schemaPath schema path of data
     * @return {@link DOMActionResult}
     */
    private static DOMActionResult invokeAction(final ContainerNode data,
            final Absolute schemaPath, final YangInstanceIdentifier yangIId, final DOMMountPoint mountPoint) {
        return invokeAction(data, schemaPath, yangIId, mountPoint.getService(DOMActionService.class)
            .orElseThrow(() -> new RestconfDocumentedException("DomAction service is missing.")));
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
    // FIXME: NETCONF-718: we should be returning a future here
    private static DOMActionResult invokeAction(final ContainerNode data, final Absolute schemaPath,
            final YangInstanceIdentifier yangIId, final DOMActionService actionService) {
        return RestconfInvokeOperationsServiceImpl.checkedGet(Futures.catching(actionService.invokeAction(
            schemaPath, new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, yangIId.getParent()), data),
            DOMActionException.class,
            cause -> new SimpleDOMActionResult(List.of(RpcResultBuilder.newError(
                ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause.getMessage()))),
            MoreExecutors.directExecutor()));
    }

    /**
     * Check the validity of the result.
     *
     * @param response response of Action
     * @return {@link DOMActionResult} result
     */
    private static DOMActionResult checkActionResponse(final DOMActionResult response) {
        if (response == null) {
            return null;
        }

        try {
            if (response.getErrors().isEmpty()) {
                return response;
            }
            LOG.debug("InvokeAction Error Message {}", response.getErrors());
            throw new RestconfDocumentedException("InvokeAction Error Message ", null, response.getErrors());
        } catch (final CancellationException e) {
            final String errMsg = "The Action Operation was cancelled while executing.";
            LOG.debug("Cancel Execution: {}", errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, e);
        }
    }
}
