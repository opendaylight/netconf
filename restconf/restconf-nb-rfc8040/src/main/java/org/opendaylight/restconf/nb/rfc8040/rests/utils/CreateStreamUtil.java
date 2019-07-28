/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil.StreamAccessMonitoringData;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationNodeContainer;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creation of data-change-event or YANG notification streams.
 */
public final class CreateStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStreamUtil.class);

    @VisibleForTesting
    static final YangInstanceIdentifier STREAMS_YIID = YangInstanceIdentifier.builder()
            .node(MonitoringModule.CONT_RESTCONF_STATE_QNAME)
            .node(MonitoringModule.CONT_STREAMS_QNAME)
            .build();
    private static final YangInstanceIdentifier.PathArgument STREAM_NAME_KEY = YangInstanceIdentifier.NodeIdentifier
            .create(MonitoringModule.LEAF_NAME_STREAM_QNAME);

    private CreateStreamUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param payload      Input of RPC - example in JSON (data-change-event stream):
     *                     <pre>
     *                     {@code
     *                         {
     *                             "input": {
     *                                 "path": "/toaster:toaster/toaster:toasterStatus",
     *                                 "sal-remote-augment:datastore": "OPERATIONAL",
     *                                 "sal-remote-augment:scope": "ONE"
     *                             }
     *                         }
     *                     }
     *                     </pre>
     * @param refSchemaCtx Reference to {@link SchemaContext} - {@link SchemaContextRef}.
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    public static DOMRpcResult createDataChangeNotifiStream(final NormalizedNodeContext payload,
            final SchemaContextRef refSchemaCtx, final TransactionChainHandler transactionChainHandler) {
        // parsing out of input RPC container that contains settings and path
        final ContainerNode data = (ContainerNode) requireNonNull(payload).getData();
        final QNameModule rpcPayloadModule = payload.getInstanceIdentifierContext().getSchemaNode()
                .getQName().getModule();
        final YangInstanceIdentifier path = preparePath(data, rpcPayloadModule);

        // building of stream name and registration of the stream in broker and monitoring module
        final NotificationOutputType outputType = prepareOutputType(data);
        final String streamName = prepareDataChangeNotifiStreamName(
                path, requireNonNull(refSchemaCtx).get(), data, outputType);
        ListenersBroker.getInstance().registerDataChangeListener(path, streamName, outputType);
        writeDataChangeStreamMonitoringData(refSchemaCtx.get(), outputType, streamName, path, transactionChainHandler);
        return getDataChangeStreamCreationOutput(rpcPayloadModule, streamName);
    }

    /**
     * Merging of the monitoring information about created stream to the operational datastore.
     *
     * @param schemaContext           Schema context.
     * @param outputType              Stream output type (JSON or XML).
     * @param streamName              Prepared stream name which identifies map entry in monitoring module.
     * @param streamPath              Data-change event path.
     * @param transactionChainHandler Transaction handler that is used for create of write-only transaction.
     */
    private static void writeDataChangeStreamMonitoringData(final SchemaContext schemaContext,
            final NotificationOutputType outputType, final String streamName, final YangInstanceIdentifier streamPath,
            final TransactionChainHandler transactionChainHandler) {
        final DOMDataTreeWriteTransaction transaction = transactionChainHandler.get().newWriteOnlyTransaction();
        final ContainerNode streamsNode = RestconfMappingNodeUtil.mapDataChangeStream(schemaContext, streamPath,
                new StreamAccessMonitoringData(SubscribeToStreamUtil.prepareUriByStreamName(streamName), outputType));
        transaction.merge(LogicalDatastoreType.OPERATIONAL, STREAMS_YIID, streamsNode);
        try {
            transaction.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Failed to merge monitoring data with data-change-event stream "
                    + "into data-store.", e);
        }
    }

    /**
     * Building of the output of the RPC that is responsible for creation of data-change event stream.
     *
     * @param outputModule Module of RPC output structure.
     * @param streamName   Name of the stream that is put into the output.
     * @return Created RPC output.
     */
    private static DOMRpcResult getDataChangeStreamCreationOutput(final QNameModule outputModule,
                                                                  final String streamName) {
        final QName outputQname = QName.create(outputModule, RestconfStreamsConstants.OUTPUT_CONTAINER_NAME);
        final QName streamNameQname = QName.create(outputModule, RestconfStreamsConstants.OUTPUT_STREAM_NAME);
        final ContainerNode output = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new NodeIdentifier(outputQname))
                .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();
        return new DefaultDOMRpcResult(output);
    }

    /**
     * Prepare {@link NotificationOutputType}.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link NotificationOutputType}.
     */
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        NotificationOutputType outputType = parseEnum(
                data, NotificationOutputType.class, RestconfStreamsConstants.OUTPUT_TYPE_PARAM_NAME);
        return outputType == null ? NotificationOutputType.XML : outputType;
    }

    /**
     * Prepare stream name.
     *
     * @param path          Path of element from which data-change-event notifications are going to be generated.
     * @param schemaContext Schema context.
     * @param data          Container with stream settings (RPC create-stream).
     * @param outputType    Notification output type (JSON or XML).
     * @return Parsed stream name.
     */
    private static String prepareDataChangeNotifiStreamName(final YangInstanceIdentifier path,
            final SchemaContext schemaContext, final ContainerNode data, NotificationOutputType outputType) {
        LogicalDatastoreType datastoreType = parseEnum(
                data, LogicalDatastoreType.class, RestconfStreamsConstants.DATASTORE_PARAM_NAME);
        datastoreType = datastoreType == null ? RestconfStreamsConstants.DEFAULT_DS : datastoreType;

        DataChangeScope scope = parseEnum(data, DataChangeScope.class, RestconfStreamsConstants.SCOPE_PARAM_NAME);
        scope = scope == null ? RestconfStreamsConstants.DEFAULT_SCOPE : scope;

        final StringBuilder streamNameBuilder = new StringBuilder();
        streamNameBuilder.append(RestconfStreamsConstants.DATA_SUBSCRIPTION)
                .append(RestconfConstants.SLASH)
                .append(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext))
                .append(RestconfStreamsConstants.DS_URI)
                .append(datastoreType)
                .append(RestconfStreamsConstants.SCOPE_URI)
                .append(scope);
        if (outputType.equals(NotificationOutputType.JSON)) {
            streamNameBuilder.append(RestconfConstants.SLASH).append(outputType.getName());
        }
        return streamNameBuilder.toString();
    }

    /**
     * Prepare {@link YangInstanceIdentifier} of stream source.
     *
     * @param data      Container with stream settings (RPC create-stream).
     * @param rpcModule Module of the input RPC context.
     * @return Parsed {@link YangInstanceIdentifier} of data element from which the data-change-event notifications
     *     are going to be generated.
     */
    private static YangInstanceIdentifier preparePath(final ContainerNode data, final QNameModule rpcModule) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> path = data.getChild(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcModule,
                        RestconfStreamsConstants.STREAM_PATH_PARAM_NAME)));
        Object pathValue = null;
        if (path.isPresent()) {
            pathValue = path.get().getValue();
        }
        if (!(pathValue instanceof YangInstanceIdentifier)) {
            LOG.warn("Instance identifier in input of create stream RPC was not normalized correctly: {}", data);
            throw new RestconfDocumentedException(
                    "Instance identifier was not normalized correctly",
                    ErrorType.APPLICATION,
                    ErrorTag.OPERATION_FAILED);
        }
        return (YangInstanceIdentifier) pathValue;
    }

    /**
     * Parsing out of enumeration from RPC create-stream body.
     *
     * @param data      Container with stream settings (RPC create-stream).
     * @param clazz     Enum type to be parsed out from input container.
     * @param paramName Local name of the enum element.
     * @return Parsed enumeration.
     */
    private static <T> T parseEnum(final ContainerNode data, final Class<T> clazz, final String paramName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> optAugNode = data.getChild(
                RestconfStreamsConstants.SAL_REMOTE_AUG_IDENTIFIER);
        if (!optAugNode.isPresent()) {
            return null;
        }
        final DataContainerChild<? extends PathArgument, ?> augNode = optAugNode.get();
        if (!(augNode instanceof AugmentationNode)) {
            return null;
        }
        final Optional<DataContainerChild<? extends PathArgument, ?>> enumNode = ((AugmentationNode) augNode).getChild(
                new NodeIdentifier(QName.create(RestconfStreamsConstants.SAL_REMOTE_AUGMENT, paramName)));
        if (!enumNode.isPresent()) {
            return null;
        }
        final Object value = enumNode.get().getValue();
        if (!(value instanceof String)) {
            return null;
        }

        return ResolveEnumUtil.resolveEnum(clazz, (String) value);
    }

    /**
     * Creation and registration of new YANG notification streams by scanning of updated schema context. New streams
     * are merged to streams container in monitoring module
     *
     * @param schemaContext Actual schema context.
     * @param transaction   R/W transaction.
     */
    public static void createYangNotifiStreams(final SchemaContext schemaContext,
                                               final DOMDataTreeReadWriteTransaction transaction) {
        final ContainerNode streamsContainer = createNotificationStreams(schemaContext, transaction);
        transaction.merge(LogicalDatastoreType.OPERATIONAL, STREAMS_YIID, streamsContainer);
    }

    /**
     * Creation of notification streams from notification definitions that are defined in input schema context and
     * haven't already been registered in previous update.
     *
     * @param updatedSchemaContext Actual schema context.
     * @param transaction          Transaction used for reading of existing notification streams.
     * @return Streams container that contains list of new notification streams.
     */
    private static ContainerNode createNotificationStreams(final SchemaContext updatedSchemaContext,
            final DOMDataTreeReadWriteTransaction transaction) {
        final Set<String> existingNotificationStreams = getExistingNotificationStreams(transaction);
        final Set<NotificationDefinition> newNotifications = collectAllNotifications(updatedSchemaContext).stream()
                .filter(notificationDefinition -> existingNotificationStreams.contains(notificationDefinition
                        .getQName().getLocalName()))
                .collect(Collectors.toSet());
        final Map<NotificationDefinition, List<StreamAccessMonitoringData>> streamData = newNotifications.stream()
                .map(notificationDefinition -> getMonitoringData(updatedSchemaContext, notificationDefinition))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        return RestconfMappingNodeUtil.mapNotificationStreams(updatedSchemaContext, streamData);
    }

    /**
     * Creation of JSON and XML notification streams and creation of monitoring data.
     *
     * @param updatedSchemaContext   Actual schema context.
     * @param notificationDefinition Schema notification.
     * @return Notification definition and list of access information (JSON and XML).
     */
    private static SimpleEntry<NotificationDefinition, ArrayList<StreamAccessMonitoringData>> getMonitoringData(
            final SchemaContext updatedSchemaContext, NotificationDefinition notificationDefinition) {
        // creation and registration of streams
        final NotificationListenerAdapter notifiStreamXML = createYangNotifiStream(
                notificationDefinition, updatedSchemaContext, NotificationOutputType.XML);
        final NotificationListenerAdapter notifiStreamJSON = createYangNotifiStream(
                notificationDefinition, updatedSchemaContext, NotificationOutputType.JSON);

        // building of stream locations
        final URI xmlStreamUri = SubscribeToStreamUtil.prepareUriByStreamName(notifiStreamXML.getStreamName());
        final URI jsonStreamUri = SubscribeToStreamUtil.prepareUriByStreamName(notifiStreamJSON.getStreamName());

        return new SimpleEntry<>(notificationDefinition, Lists.newArrayList(
                new StreamAccessMonitoringData(xmlStreamUri, NotificationOutputType.XML),
                new StreamAccessMonitoringData(jsonStreamUri, NotificationOutputType.JSON)));
    }

    /**
     * Reading of stream names of already saved YANG notifications streams in operational datastore.
     *
     * @param transaction R/W transaction.
     * @return Set of stream names of existing YANG notification streams.
     */
    private static Set<String> getExistingNotificationStreams(final DOMDataTreeReadWriteTransaction transaction) {
        try {
            final Optional<NormalizedNode<?, ?>> normalizedNode = transaction.read(
                    LogicalDatastoreType.OPERATIONAL, STREAMS_YIID).get();
            return normalizedNode.map(node -> ((ContainerNode) node).getValue().stream()
                    .filter(childNode -> childNode.getNodeType().equals(MonitoringModule.LIST_STREAM_QNAME))
                    .flatMap(streamList -> ((MapNode) streamList).getValue().stream()
                            .map(streamMapNode -> streamMapNode.getChild(STREAM_NAME_KEY))
                            .filter(Optional::isPresent)
                            .map(streamNameLeaf -> (String) streamNameLeaf.get().getValue()))
                    .collect(Collectors.toSet()))
                    .orElse(Collections.emptySet());
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Failed to create notification streams: Unable to read list of existing "
                    + "notification streams", e);
        }
    }

    /**
     * Collecting of all notification definitions that are placed directly or indirectly under input
     * {@link DataNodeContainer}.
     *
     * @param nodeContainer Node that is searched for all notification definitions.
     * @return {@link HashSet} of collected notification definitions.
     */
    private static Set<NotificationDefinition> collectAllNotifications(final DataNodeContainer nodeContainer) {
        final Set<NotificationDefinition> collectedNotifications = new HashSet<>();
        if (nodeContainer instanceof NotificationNodeContainer) {
            collectedNotifications.addAll((((NotificationNodeContainer) nodeContainer).getNotifications()));
        }
        final List<NotificationDefinition> subNotifications = nodeContainer.getChildNodes().stream()
                .filter(dataSchemaNode -> dataSchemaNode instanceof DataNodeContainer)
                .flatMap(node -> collectAllNotifications((DataNodeContainer) node).stream())
                .collect(Collectors.toList());
        collectedNotifications.addAll(subNotifications);
        return collectedNotifications;
    }

    /**
     * Create YANG notification stream using notification definition in YANG schema.
     *
     * @param notificationDefinition YANG notification definition.
     * @param schemaContext          Actual schema context.
     * @param outputType             Output type (XML or JSON).
     * @return {@link NotificationListenerAdapter}
     */
    private static NotificationListenerAdapter createYangNotifiStream(
            final NotificationDefinition notificationDefinition, final SchemaContext schemaContext,
            final NotificationOutputType outputType) {
        final String streamName = parseNotificationStreamName(requireNonNull(notificationDefinition),
                requireNonNull(schemaContext), requireNonNull(outputType.getName()));
        return ListenersBroker.getInstance().registerNotificationListener(notificationDefinition.getPath(),
                streamName, outputType);
    }

    private static String parseNotificationStreamName(final NotificationDefinition notificationDefinition,
            final SchemaContext schemaContext, final String outputType) {
        String serializedSchemaPath = SchemaPathCodec.serialize(
                notificationDefinition.getPath(), schemaContext);
        if (serializedSchemaPath.startsWith(String.valueOf(RestconfConstants.DOT))) {
            serializedSchemaPath = serializedSchemaPath.substring(1);
        }

        final StringBuilder streamNameBuilder = new StringBuilder();
        streamNameBuilder.append(RestconfStreamsConstants.NOTIFICATION_STREAM);
        if (!serializedSchemaPath.equals(String.valueOf(RestconfConstants.SLASH))) {
            streamNameBuilder.append(serializedSchemaPath);
        }

        if (outputType.equals(NotificationOutputType.JSON.getName())) {
            streamNameBuilder.append('/').append(NotificationOutputType.JSON.getName());
        }
        return streamNameBuilder.toString();
    }
}