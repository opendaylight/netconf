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
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
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
import org.opendaylight.restconf.common.patch.Patch;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.monitoring.RestconfStateStreams;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Data;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
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

    public RestconfDataServiceImpl(final DatabindProvider databindProvider,
            final DOMDataBroker dataBroker, final DOMMountPointService  mountPointService,
            final RestconfStreamsSubscriptionService delegRestconfSubscrService,
            final DOMActionService actionService, final StreamsConfiguration configuration) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        restconfStrategy = new MdsalRestconfStrategy(dataBroker);
        this.mountPointService = requireNonNull(mountPointService);
        this.delegRestconfSubscrService = requireNonNull(delegRestconfSubscrService);
        this.actionService = requireNonNull(actionService);
        streamUtils = configuration.useSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
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
                    createYangNotifiStream(moduleName, notifName, NotificationOutputType.XML));
                writeNotificationStreamToDatastore(schemaContext, uriInfo, transaction,
                    createYangNotifiStream(moduleName, notifName, NotificationOutputType.JSON));
            });
        }

        try {
            transaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    private static NotificationListenerAdapter createYangNotifiStream(final String moduleName, final QName notifName,
            final NotificationOutputType outputType) {
        final var streamName = createNotificationStreamName(moduleName, notifName.getLocalName(), outputType);
        final var listenersBroker = ListenersBroker.getInstance();

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
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param payload data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response putData(@Encoded @PathParam("identifier") final String identifier,
            final NormalizedNodePayload payload, @Context final UriInfo uriInfo) {
        requireNonNull(payload);

        final WriteDataParams params = QueryParams.newWriteDataParams(uriInfo);

        final InstanceIdentifierContext iid = payload.getInstanceIdentifierContext();
        final YangInstanceIdentifier path = iid.getInstanceIdentifier();

        validInputData(iid.getSchemaNode() != null, payload);
        validTopLevelNodeName(path, payload);
        validateListKeysEqualityInPayloadAndUri(payload);

        final RestconfStrategy strategy = getRestconfStrategy(iid.getMountPoint());
        return PutDataTransactionUtil.putData(path, payload.getData(), iid.getSchemaContext(), strategy, params);
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param payload new data
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response postData(@Encoded @PathParam("identifier") final String identifier,
            final NormalizedNodePayload payload, @Context final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    /**
     * Create a data resource.
     *
     * @param payload new data
     * @param uriInfo URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public Response postData(final NormalizedNodePayload payload, @Context final UriInfo uriInfo) {
        requireNonNull(payload);
        final InstanceIdentifierContext iid = payload.getInstanceIdentifierContext();
        if (iid.getSchemaNode() instanceof ActionDefinition) {
            return invokeAction(payload);
        }

        final WriteDataParams params = QueryParams.newWriteDataParams(uriInfo);
        final RestconfStrategy strategy = getRestconfStrategy(iid.getMountPoint());
        return PostDataTransactionUtil.postData(uriInfo, iid.getInstanceIdentifier(), payload.getData(), strategy,
            iid.getSchemaContext(), params);
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
     * Ordered list of edits that are applied to the target datastore by the server.
     *
     * @param identifier path to target
     * @param context edits
     * @param uriInfo URI info
     * @return {@link PatchStatusContext}
     */
    @Patch
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_PATCH_JSON,
        MediaTypes.APPLICATION_YANG_PATCH_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext patchData(@Encoded @PathParam("identifier") final String identifier,
            final PatchContext context, @Context final UriInfo uriInfo) {
        return patchData(context, uriInfo);
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server.
     *
     * @param context
     *            edits
     * @param uriInfo
     *            URI info
     * @return {@link PatchStatusContext}
     */
    @Patch
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_PATCH_JSON,
        MediaTypes.APPLICATION_YANG_PATCH_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public PatchStatusContext patchData(final PatchContext context, @Context final UriInfo uriInfo) {
        final InstanceIdentifierContext iid = RestconfDocumentedException.throwIfNull(context,
            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, "No patch documented provided")
            .getInstanceIdentifierContext();
        final RestconfStrategy strategy = getRestconfStrategy(iid.getMountPoint());
        return PatchDataTransactionUtil.patchData(context, strategy, iid.getSchemaContext());
    }

    /**
     * Partially modify the target data resource.
     *
     * @param identifier path to target
     * @param payload data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @Patch
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void patchData(@Encoded @PathParam("identifier") final String identifier,
            final NormalizedNodePayload payload, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final InstanceIdentifierContext iid = payload.getInstanceIdentifierContext();
        final YangInstanceIdentifier path = iid.getInstanceIdentifier();
        validInputData(iid.getSchemaNode() != null, payload);
        validTopLevelNodeName(path, payload);
        validateListKeysEqualityInPayloadAndUri(payload);
        final var strategy = getRestconfStrategy(iid.getMountPoint());

        Futures.addCallback(strategy.merge(path, payload.getData(), iid.getSchemaContext()), new FutureCallback<>() {
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
    public Response invokeAction(final NormalizedNodePayload payload) {
        final InstanceIdentifierContext context = payload.getInstanceIdentifierContext();
        final YangInstanceIdentifier yangIIdContext = context.getInstanceIdentifier();
        final NormalizedNode data = payload.getData();

        if (yangIIdContext.isEmpty() && !Data.QNAME.equals(data.name().getNodeType())) {
            throw new RestconfDocumentedException("Instance identifier need to contain at least one path argument",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        final DOMMountPoint mountPoint = context.getMountPoint();
        final Absolute schemaPath = context.inference().toSchemaInferenceStack().toSchemaNodeIdentifier();
        final DOMActionResult response;
        if (mountPoint != null) {
            response = invokeAction((ContainerNode) data, schemaPath, yangIIdContext, mountPoint);
        } else {
            response = invokeAction((ContainerNode) data, schemaPath, yangIIdContext, actionService);
        }
        final DOMActionResult result = checkActionResponse(response);

        ContainerNode resultData = null;
        if (result != null) {
            resultData = result.getOutput().orElse(null);
        }

        if (resultData != null && resultData.isEmpty()) {
            return Response.status(Status.NO_CONTENT).build();
        }

        return Response.status(Status.OK)
            .entity(NormalizedNodePayload.ofNullable(context, resultData))
            .build();
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

    /**
     * Valid input data based on presence of a schema node.
     *
     * @param haveSchemaNode true if there is an underlying schema node
     * @param payload    input data
     */
    @VisibleForTesting
    static void validInputData(final boolean haveSchemaNode, final NormalizedNodePayload payload) {
        final boolean haveData = payload.getData() != null;
        if (haveSchemaNode) {
            if (!haveData) {
                throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE);
            }
        } else if (haveData) {
            throw new RestconfDocumentedException("No input expected.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
    }

    /**
     * Valid top level node name.
     *
     * @param path    path of node
     * @param payload data
     */
    @VisibleForTesting
    static void validTopLevelNodeName(final YangInstanceIdentifier path, final NormalizedNodePayload payload) {
        final QName dataNodeType = payload.getData().name().getNodeType();
        if (path.isEmpty()) {
            if (!Data.QNAME.equals(dataNodeType)) {
                throw new RestconfDocumentedException("Instance identifier has to contain at least one path argument",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        } else {
            final String identifierName = path.getLastPathArgument().getNodeType().getLocalName();
            final String payloadName = dataNodeType.getLocalName();
            if (!payloadName.equals(identifierName)) {
                throw new RestconfDocumentedException(
                        "Payload name (" + payloadName + ") is different from identifier name (" + identifierName + ")",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        }
    }

    /**
     * Validates whether keys in {@code payload} are equal to values of keys in
     * {@code iiWithData} for list schema node.
     *
     * @throws RestconfDocumentedException if key values or key count in payload and URI isn't equal
     */
    @VisibleForTesting
    static void validateListKeysEqualityInPayloadAndUri(final NormalizedNodePayload payload) {
        final InstanceIdentifierContext iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode data = payload.getData();
        if (schemaNode instanceof ListSchemaNode listSchema) {
            final var keyDefinitions = listSchema.getKeyDefinition();
            if (lastPathArgument instanceof NodeIdentifierWithPredicates && data instanceof MapEntryNode) {
                final Map<QName, Object> uriKeyValues = ((NodeIdentifierWithPredicates) lastPathArgument).asMap();
                isEqualUriAndPayloadKeyValues(uriKeyValues, (MapEntryNode) data, keyDefinitions);
            }
        }
    }

    private static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
            final List<QName> keyDefinitions) {
        final Map<QName, Object> mutableCopyUriKeyValues = new HashMap<>(uriKeyValues);
        for (final QName keyDefinition : keyDefinitions) {
            final Object uriKeyValue = RestconfDocumentedException.throwIfNull(
                    mutableCopyUriKeyValues.remove(keyDefinition), ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    "Missing key %s in URI.", keyDefinition);

            final Object dataKeyValue = payload.name().getValue(keyDefinition);

            if (!uriKeyValue.equals(dataKeyValue)) {
                final String errMsg = "The value '" + uriKeyValue + "' for key '" + keyDefinition.getLocalName()
                        + "' specified in the URI doesn't match the value '" + dataKeyValue
                        + "' specified in the message body. ";
                throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }
}
