/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PostPutQueryParameters.INSERT;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PostPutQueryParameters.POINT;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.NOTIFICATION_STREAM;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAMS_PATH;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_ACCESS_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_LOCATION_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_PATH;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_PATH_PART;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PlainPatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PostPutQueryParameters.Insert;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfDataService}.
 */
@Path("/")
public class RestconfDataServiceImpl implements RestconfDataService {
    // FIXME: we should be able to interpret 'point' and refactor this class into a behavior
    private static final class QueryParams implements Immutable {
        final @Nullable String point;
        final @Nullable Insert insert;

        QueryParams(final @Nullable Insert insert, final @Nullable String point) {
            this.insert = insert;
            this.point = point;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataServiceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final RestconfStreamsSubscriptionService delegRestconfSubscrService;
    private final SchemaContextHandler schemaContextHandler;
    private final MdsalRestconfStrategy restconfStrategy;
    private final DOMMountPointService mountPointService;
    private final SubscribeToStreamUtil streamUtils;
    private final DOMActionService actionService;
    private final DOMDataBroker dataBroker;

    public RestconfDataServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMDataBroker dataBroker, final DOMMountPointService  mountPointService,
            final RestconfStreamsSubscriptionService delegRestconfSubscrService,
            final DOMActionService actionService, final Configuration configuration) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.dataBroker = requireNonNull(dataBroker);
        this.restconfStrategy = new MdsalRestconfStrategy(dataBroker);
        this.mountPointService = requireNonNull(mountPointService);
        this.delegRestconfSubscrService = requireNonNull(delegRestconfSubscrService);
        this.actionService = requireNonNull(actionService);
        streamUtils = configuration.isUseSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return readData(null, uriInfo);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        final EffectiveModelContext schemaContextRef = this.schemaContextHandler.get();
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef, Optional.of(mountPointService));
        final WriterParameters parameters = ReadDataTransactionUtil.parseUriParameters(instanceIdentifier, uriInfo);

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();

        // FIXME: this looks quite crazy, why do we even have it?
        if (mountPoint == null && identifier != null && identifier.contains(STREAMS_PATH)
            && !identifier.contains(STREAM_PATH_PART)) {
            createAllYangNotificationStreams(schemaContextRef, uriInfo);
        }

        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        final NormalizedNode node;
        if (parameters.getFieldPaths() != null && !parameters.getFieldPaths().isEmpty()) {
            node = ReadDataTransactionUtil.readData(parameters.getContent(), instanceIdentifier.getInstanceIdentifier(),
                    strategy, parameters.getWithDefault(), schemaContextRef, parameters.getFieldPaths());
        } else {
            node = ReadDataTransactionUtil.readData(parameters.getContent(), instanceIdentifier.getInstanceIdentifier(),
                    strategy, parameters.getWithDefault(), schemaContextRef);
        }

        // FIXME: this is utter craziness, refactor it properly!
        if (identifier != null && identifier.contains(STREAM_PATH) && identifier.contains(STREAM_ACCESS_PATH_PART)
                && identifier.contains(STREAM_LOCATION_PATH_PART)) {
            final String value = (String) node.body();
            final String streamName = value.substring(value.indexOf(NOTIFICATION_STREAM + '/'));
            this.delegRestconfSubscrService.subscribeToStream(streamName, uriInfo);
        }
        if (node == null) {
            throw new RestconfDocumentedException(
                    "Request could not be completed because the relevant data model content does not exist",
                    ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        }

        if (parameters.getContent().equals(RestconfDataServiceConstant.ReadData.ALL)
                    || parameters.getContent().equals(RestconfDataServiceConstant.ReadData.CONFIG)) {
            final QName type = node.getIdentifier().getNodeType();
            return Response.status(Status.OK)
                    .entity(new NormalizedNodeContext(instanceIdentifier, node, parameters))
                    .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null)
                        + "-" + type.getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
        }

        return Response.status(Status.OK)
            .entity(new NormalizedNodeContext(instanceIdentifier, node, parameters))
            .build();
    }

    private void createAllYangNotificationStreams(final EffectiveModelContext schemaContext, final UriInfo uriInfo) {
        final DOMDataTreeWriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        for (final NotificationDefinition notificationDefinition : schemaContext.getNotifications()) {
            writeNotificationStreamToDatastore(schemaContext, uriInfo, transaction,
                CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContext,
                    NotificationOutputType.XML));
            writeNotificationStreamToDatastore(schemaContext, uriInfo, transaction,
                CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContext,
                    NotificationOutputType.JSON));
        }
        try {
            transaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    private void writeNotificationStreamToDatastore(final EffectiveModelContext schemaContext,
            final UriInfo uriInfo, final DOMDataTreeWriteOperations tx, final NotificationListenerAdapter listener) {
        final URI uri = streamUtils.prepareUriByStreamName(uriInfo, listener.getStreamName());
        final MapEntryNode mapToStreams = RestconfMappingNodeUtil.mapYangNotificationStreamByIetfRestconfMonitoring(
                listener.getSchemaPath().lastNodeIdentifier(), schemaContext.getNotifications(), null,
                listener.getOutputType(), uri);

        tx.merge(LogicalDatastoreType.OPERATIONAL,
            Rfc8040.restconfStateStreamPath(mapToStreams.getIdentifier()), mapToStreams);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);

        final QueryParams checkedParms = checkQueryParameters(uriInfo);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload.getInstanceIdentifierContext();

        validInputData(iid.getSchemaNode(), payload);
        validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final EffectiveModelContext ref = mountPoint == null
                ? this.schemaContextHandler.get() : modelContext(mountPoint);

        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        return PutDataTransactionUtil.putData(payload, ref, strategy, checkedParms.insert, checkedParms.point);
    }

    private static QueryParams checkQueryParameters(final UriInfo uriInfo) {
        boolean insertUsed = false;
        boolean pointUsed = false;
        Insert insert = null;
        String point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case INSERT:
                    if (insertUsed) {
                        throw new RestconfDocumentedException("Insert parameter can be used only once.",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
                    }

                    insertUsed = true;
                    final String str = entry.getValue().get(0);
                    insert = Insert.forValue(str);
                    if (insert == null) {
                        throw new RestconfDocumentedException("Unrecognized insert parameter value '" + str + "'",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
                    }
                    break;
                case POINT:
                    if (pointUsed) {
                        throw new RestconfDocumentedException("Point parameter can be used only once.",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
                    }

                    pointUsed = true;
                    point = entry.getValue().get(0);
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey(),
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
        }

        checkQueryParams(insertUsed, pointUsed, insert);
        return new QueryParams(insert, point);
    }

    private static void checkQueryParams(final boolean insertUsed, final boolean pointUsed, final Insert insert) {
        if (pointUsed) {
            if (!insertUsed) {
                throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.",
                    ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }

            if (insert != Insert.BEFORE && insert != Insert.AFTER) {
                throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.",
                    ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
        }
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);
        if (payload.getInstanceIdentifierContext().getSchemaNode() instanceof ActionDefinition) {
            return invokeAction(payload);
        }

        final QueryParams checkedParms = checkQueryParameters(uriInfo);
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        return PostDataTransactionUtil.postData(uriInfo, payload, strategy,
                getSchemaContext(mountPoint), checkedParms.insert, checkedParms.point);
    }

    @Override
    public Response deleteData(final String identifier) {
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, this.schemaContextHandler.get(), Optional.of(mountPointService));

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        return DeleteDataTransactionUtil.deleteData(strategy, instanceIdentifier.getInstanceIdentifier());
    }

    @Override
    public PatchStatusContext patchData(final String identifier, final PatchContext context, final UriInfo uriInfo) {
        return patchData(context, uriInfo);
    }

    @Override
    public PatchStatusContext patchData(final PatchContext context, final UriInfo uriInfo) {
        final DOMMountPoint mountPoint = requireNonNull(context).getInstanceIdentifierContext().getMountPoint();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        return PatchDataTransactionUtil.patchData(context, strategy, getSchemaContext(mountPoint));
    }

    @Override
    public Response patchData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();

        validInputData(iid.getSchemaNode(), payload);
        validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final EffectiveModelContext ref = mountPoint == null
                ? this.schemaContextHandler.get() : modelContext(mountPoint);
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);

        return PlainPatchDataTransactionUtil.patchData(payload, strategy, ref);
    }

    private EffectiveModelContext getSchemaContext(final DOMMountPoint mountPoint) {
        return mountPoint == null ? schemaContextHandler.get() : modelContext(mountPoint);
    }

    // FIXME: why is this synchronized?
    public synchronized RestconfStrategy getRestconfStrategy(final DOMMountPoint mountPoint) {
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
     * @param payload {@link NormalizedNodeContext} - the body of the operation
     * @return {@link NormalizedNodeContext} wrapped in {@link Response}
     */
    public Response invokeAction(final NormalizedNodeContext payload) {
        final InstanceIdentifierContext<?> context = payload.getInstanceIdentifierContext();
        final DOMMountPoint mountPoint = context.getMountPoint();
        final Absolute schemaPath = Absolute.of(ImmutableList.copyOf(context.getSchemaNode().getPath()
            .getPathFromRoot()));
        final YangInstanceIdentifier yangIIdContext = context.getInstanceIdentifier();
        final NormalizedNode data = payload.getData();

        if (yangIIdContext.isEmpty()
            && !RestconfDataServiceConstant.NETCONF_BASE_QNAME.equals(data.getIdentifier().getNodeType())) {
            throw new RestconfDocumentedException("Instance identifier need to contain at least one path argument",
                ErrorType.PROTOCOL, RestconfDocumentedException.MALFORMED_MESSAGE);
        }

        final DOMActionResult response;
        final EffectiveModelContext schemaContextRef;
        if (mountPoint != null) {
            response = RestconfInvokeOperationsUtil.invokeAction((ContainerNode) data, schemaPath, yangIIdContext,
                mountPoint);
            schemaContextRef = modelContext(mountPoint);
        } else {
            response = RestconfInvokeOperationsUtil.invokeAction((ContainerNode) data, schemaPath, yangIIdContext,
                actionService);
            schemaContextRef = this.schemaContextHandler.get();
        }
        final DOMActionResult result = RestconfInvokeOperationsUtil.checkActionResponse(response);

        ActionDefinition resultNodeSchema = null;
        ContainerNode resultData = null;
        if (result != null) {
            final Optional<ContainerNode> optOutput = result.getOutput();
            if (optOutput.isPresent()) {
                resultData = optOutput.get();
                resultNodeSchema = (ActionDefinition) context.getSchemaNode();
            }
        }

        if (resultData != null && resultData.isEmpty()) {
            return Response.status(Status.NO_CONTENT).build();
        }

        return Response.status(Status.OK)
            .entity(new NormalizedNodeContext(
                new InstanceIdentifierContext<>(yangIIdContext, resultNodeSchema, mountPoint, schemaContextRef),
                resultData))
            .build();
    }

    /**
     * Valid input data with {@link SchemaNode}.
     *
     * @param schemaNode {@link SchemaNode}
     * @param payload    input data
     */
    @VisibleForTesting
    public static void validInputData(final SchemaNode schemaNode, final NormalizedNodeContext payload) {
        if (schemaNode != null && payload.getData() == null) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL,
                RestconfDocumentedException.MALFORMED_MESSAGE);
        } else if (schemaNode == null && payload.getData() != null) {
            throw new RestconfDocumentedException("No input expected.", ErrorType.PROTOCOL,
                RestconfDocumentedException.MALFORMED_MESSAGE);
        }
    }

    /**
     * Valid top level node name.
     *
     * @param path    path of node
     * @param payload data
     */
    @VisibleForTesting
    public static void validTopLevelNodeName(final YangInstanceIdentifier path, final NormalizedNodeContext payload) {
        final String payloadName = payload.getData().getIdentifier().getNodeType().getLocalName();

        if (path.isEmpty()) {
            if (!payload.getData().getIdentifier().getNodeType().equals(
                RestconfDataServiceConstant.NETCONF_BASE_QNAME)) {
                throw new RestconfDocumentedException("Instance identifier has to contain at least one path argument",
                        ErrorType.PROTOCOL, RestconfDocumentedException.MALFORMED_MESSAGE);
            }
        } else {
            final String identifierName = path.getLastPathArgument().getNodeType().getLocalName();
            if (!payloadName.equals(identifierName)) {
                throw new RestconfDocumentedException(
                        "Payload name (" + payloadName + ") is different from identifier name (" + identifierName + ")",
                        ErrorType.PROTOCOL, RestconfDocumentedException.MALFORMED_MESSAGE);
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
    public static void validateListKeysEqualityInPayloadAndUri(final NormalizedNodeContext payload) {
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode data = payload.getData();
        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> keyDefinitions = ((ListSchemaNode) schemaNode).getKeyDefinition();
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

            final Object dataKeyValue = payload.getIdentifier().getValue(keyDefinition);

            if (!uriKeyValue.equals(dataKeyValue)) {
                final String errMsg = "The value '" + uriKeyValue + "' for key '" + keyDefinition.getLocalName()
                        + "' specified in the URI doesn't match the value '" + dataKeyValue
                        + "' specified in the message body. ";
                throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
