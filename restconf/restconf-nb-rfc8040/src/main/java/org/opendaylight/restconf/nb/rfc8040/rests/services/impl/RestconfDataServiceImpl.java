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

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.ActionServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
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
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
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
    private final SubscribeToStreamUtil streamUtils;

    // FIXME: evaluate thread-safety of updates (synchronized) vs. access (mostly unsynchronized) here
    private SchemaContextHandler schemaContextHandler;
    private TransactionChainHandler transactionChainHandler;
    private DOMMountPointServiceHandler mountPointServiceHandler;
    private volatile ActionServiceHandler actionServiceHandler;

    public RestconfDataServiceImpl(final SchemaContextHandler schemaContextHandler,
            final TransactionChainHandler transactionChainHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler,
            final RestconfStreamsSubscriptionService delegRestconfSubscrService,
            final ActionServiceHandler actionServiceHandler,
            final Configuration configuration) {
        this.actionServiceHandler = requireNonNull(actionServiceHandler);
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        this.mountPointServiceHandler = requireNonNull(mountPointServiceHandler);
        this.delegRestconfSubscrService = requireNonNull(delegRestconfSubscrService);
        streamUtils = configuration.isUseSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
    }

    @Override
    public synchronized void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            } else if (object instanceof ActionServiceHandler) {
                actionServiceHandler = (ActionServiceHandler) object;
            } else if (object instanceof DOMMountPointServiceHandler) {
                mountPointServiceHandler = (DOMMountPointServiceHandler) object;
            } else if (object instanceof TransactionChainHandler) {
                transactionChainHandler = (TransactionChainHandler) object;
            }
        }
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return readData(null, uriInfo);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        final EffectiveModelContext schemaContextRef = this.schemaContextHandler.get();
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef, Optional.of(this.mountPointServiceHandler.get()));
        final WriterParameters parameters = ReadDataTransactionUtil.parseUriParameters(instanceIdentifier, uriInfo);

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
        final NormalizedNode<?, ?> node = readData(identifier, parameters.getContent(),
                instanceIdentifier.getInstanceIdentifier(), strategy, parameters.getWithDefault(), schemaContextRef,
                uriInfo);
        if (identifier != null && identifier.contains(STREAM_PATH) && identifier.contains(STREAM_ACCESS_PATH_PART)
                && identifier.contains(STREAM_LOCATION_PATH_PART)) {
            final String value = (String) node.getValue();
            final String streamName = value.substring(value.indexOf(NOTIFICATION_STREAM + '/'));
            this.delegRestconfSubscrService.subscribeToStream(streamName, uriInfo);
        }
        if (node == null) {
            throw new RestconfDocumentedException(
                    "Request could not be completed because the relevant data model content does not exist",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.DATA_MISSING);
        }

        if (parameters.getContent().equals(RestconfDataServiceConstant.ReadData.ALL)
                    || parameters.getContent().equals(RestconfDataServiceConstant.ReadData.CONFIG)) {
            final QName type = node.getNodeType();
            return Response.status(200)
                    .entity(new NormalizedNodeContext(instanceIdentifier, node, parameters))
                    .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null)
                        + "-" + type.getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
        }

        return Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node, parameters)).build();
    }

    /**
     * Read specific type of data from data store via transaction and if identifier read data from
     * streams then put streams from actual schema context to datastore.
     *
     * @param identifier    identifier of data to read
     * @param content       type of data to read (config, state, all)
     * @param strategy      {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa      value of with-defaults parameter
     * @param schemaContext schema context
     * @param uriInfo       uri info
     * @return {@link NormalizedNode}
     */
    private NormalizedNode<?, ?> readData(final String identifier, final String content,
            final YangInstanceIdentifier path, final RestconfStrategy strategy, final String withDefa,
            final EffectiveModelContext schemaContext, final UriInfo uriInfo) {
        if (identifier != null && identifier.contains(STREAMS_PATH) && !identifier.contains(STREAM_PATH_PART)) {
            createAllYangNotificationStreams(strategy, schemaContext, uriInfo);
        }
        return ReadDataTransactionUtil.readData(content, path, strategy, withDefa, schemaContext);
    }

    private void createAllYangNotificationStreams(final RestconfStrategy strategy,
            final EffectiveModelContext schemaContext, final UriInfo uriInfo) {
        strategy.prepareReadWriteExecution();
        final boolean exist = checkExist(schemaContext, strategy);

        for (final NotificationDefinition notificationDefinition : schemaContext.getNotifications()) {
            final NotificationListenerAdapter notifiStreamXML =
                CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContext,
                    NotificationOutputType.XML);
            final NotificationListenerAdapter notifiStreamJSON =
                CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContext,
                    NotificationOutputType.JSON);
            writeNotificationStreamToDatastore(schemaContext, uriInfo, strategy, exist, notifiStreamXML);
            writeNotificationStreamToDatastore(schemaContext, uriInfo, strategy, exist, notifiStreamJSON);
        }
        try {
            strategy.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    private void writeNotificationStreamToDatastore(final EffectiveModelContext schemaContext,
            final UriInfo uriInfo, final RestconfStrategy strategy, final boolean exist,
            final NotificationListenerAdapter listener) {
        final URI uri = streamUtils.prepareUriByStreamName(uriInfo, listener.getStreamName());
        final NormalizedNode<?, ?> mapToStreams =
            RestconfMappingNodeUtil.mapYangNotificationStreamByIetfRestconfMonitoring(
                listener.getSchemaPath().getLastComponent(), schemaContext.getNotifications(), null,
                listener.getOutputType(), uri, SubscribeToStreamUtil.getMonitoringModule(schemaContext), exist);
        writeDataToDS(schemaContext,
            listener.getSchemaPath().getLastComponent().getLocalName(), strategy, exist, mapToStreams);
    }

    private static boolean checkExist(final EffectiveModelContext schemaContext, final RestconfStrategy strategy) {
        try {
            return strategy.exists(LogicalDatastoreType.OPERATIONAL,
                IdentifierCodec.deserialize(Rfc8040.MonitoringModule.PATH_TO_STREAMS, schemaContext)).get();
        } catch (final InterruptedException | ExecutionException exception) {
            throw new RestconfDocumentedException("Problem while checking data if exists", exception);
        }
    }

    private static void writeDataToDS(final EffectiveModelContext schemaContext, final String name,
                                      final RestconfStrategy strategy, final boolean exist,
                                      final NormalizedNode<?, ?> mapToStreams) {
        final String pathId;
        if (exist) {
            pathId = Rfc8040.MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY + name;
        } else {
            pathId = Rfc8040.MonitoringModule.PATH_TO_STREAMS;
        }
        strategy.merge(LogicalDatastoreType.OPERATIONAL, IdentifierCodec.deserialize(pathId, schemaContext),
            mapToStreams);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);

        final QueryParams checkedParms = checkQueryParameters(uriInfo);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload.getInstanceIdentifierContext();

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final EffectiveModelContext ref = mountPoint == null
                ? this.schemaContextHandler.get()
                : mountPoint.getEffectiveModelContext();

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
                            RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
                    }

                    insertUsed = true;
                    final String str = entry.getValue().get(0);
                    insert = Insert.valueOf(str);
                    if (insert == null) {
                        throw new RestconfDocumentedException("Unrecognized insert parameter value '" + str + "'",
                            RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
                    }
                    break;
                case POINT:
                    if (pointUsed) {
                        throw new RestconfDocumentedException("Point parameter can be used only once.",
                            RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
                    }

                    pointUsed = true;
                    point = entry.getValue().get(0);
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey(),
                            RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
            }
        }

        checkQueryParams(insertUsed, pointUsed, insert);
        return new QueryParams(insert, point);
    }

    private static void checkQueryParams(final boolean insertUsed, final boolean pointUsed, final Insert insert) {
        if (pointUsed) {
            if (!insertUsed) {
                throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.",
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
            }

            if (insert != Insert.BEFORE || insert != Insert.AFTER) {
                throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.",
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ELEMENT);
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
                identifier, this.schemaContextHandler.get(), Optional.of(this.mountPointServiceHandler.get()));

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

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final EffectiveModelContext ref = mountPoint == null
                ? this.schemaContextHandler.get()
                : mountPoint.getEffectiveModelContext();
        final RestconfStrategy strategy = getRestconfStrategy(mountPoint);

        return PlainPatchDataTransactionUtil.patchData(payload, strategy, ref);
    }

    private EffectiveModelContext getSchemaContext(final DOMMountPoint mountPoint) {
        return mountPoint == null ? schemaContextHandler.get() : mountPoint.getEffectiveModelContext();
    }

    // FIXME: why is this synchronized?
    public synchronized RestconfStrategy getRestconfStrategy(final DOMMountPoint mountPoint) {
        if (mountPoint == null) {
            return new MdsalRestconfStrategy(transactionChainHandler);
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
        final SchemaPath schemaPath = context.getSchemaNode().getPath();
        final YangInstanceIdentifier yangIIdContext = context.getInstanceIdentifier();
        final NormalizedNode<?, ?> data = payload.getData();

        if (yangIIdContext.isEmpty() && !RestconfDataServiceConstant.NETCONF_BASE_QNAME.equals(data.getNodeType())) {
            throw new RestconfDocumentedException("Instance identifier need to contain at least one path argument",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        final DOMActionResult response;
        final EffectiveModelContext schemaContextRef;
        if (mountPoint != null) {
            response = RestconfInvokeOperationsUtil.invokeActionViaMountPoint(mountPoint, (ContainerNode) data,
                schemaPath, yangIIdContext);
            schemaContextRef = mountPoint.getEffectiveModelContext();
        } else {
            response = RestconfInvokeOperationsUtil.invokeAction((ContainerNode) data, schemaPath,
                this.actionServiceHandler, yangIIdContext);
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

        if (resultData != null && resultData.getValue().isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }

        return Response.status(200).entity(new NormalizedNodeContext(new InstanceIdentifierContext<>(yangIIdContext,
                resultNodeSchema, mountPoint, schemaContextRef), resultData)).build();
    }
}
