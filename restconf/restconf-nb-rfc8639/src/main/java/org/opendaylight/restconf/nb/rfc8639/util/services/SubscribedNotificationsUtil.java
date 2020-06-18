/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.util.services;

import com.google.common.collect.Sets;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.Encoding;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscribedNotificationsModuleUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev190909.update.policy.modifiable.UpdateTrigger;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SubscribedNotificationsUtil {
    private static final String PREFIXED_NOTIFICATION_STREAM_NAME_STR = "[a-zA-Z_0-9\\-]+:[a-zA-Z_0-9\\-]+";
    private static final XMLOutputFactory XML_FACTORY;

    private static final Logger LOG = LoggerFactory.getLogger(SubscribedNotificationsUtil.class);

    static {
        XML_FACTORY = XMLOutputFactory.newInstance();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    public static final Pattern PREFIXED_NOTIFICATION_STREAM_NAME_PATTERN = Pattern.compile(
            PREFIXED_NOTIFICATION_STREAM_NAME_STR);

    private SubscribedNotificationsUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Create URI for stream.
     *
     * @param uriInfo
     *            - base URI information from request
     * @param streamName
     *            - path of stream
     * @return URI of stream
     */
    public static URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
        final URI baseUri = uriInfo.getBaseUri();

        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        final UriBuilder uriToServer = uriBuilder.port(baseUri.getPort()).scheme("https");
        return uriToServer.replacePath("/restconf/notification/" + streamName).build();
    }

    /**
     * Converts QName of a YANG statement to a String consisting of module name prefix and statement local name
     * separated by colon.
     *
     * @param statement YANG statement QName
     * @param schemaContext global schema context
     *
     * @return prefixed statement name
     */
    public static String qNameToModulePrefixAndName(final QName statement, final SchemaContext schemaContext) {
        final Module module = schemaContext.findModule(statement.getModule()).get();
        final String name = statement.getLocalName();
        return module.getName() + ":" + name;
    }

    public static String timeStampToRFC3339Format(final Instant timeStamp) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(timeStamp,
                ZoneId.systemDefault()));
    }

    public static boolean checkNodeExistInDatastore(final SchemaContext schemaContext,
            final DOMDataTreeReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastoreType,
            final String pathToNode, final DOMTransactionChain domTransactionChain) {
        try {
            return readWriteTransaction.exists(datastoreType, IdentifierCodec.deserialize(pathToNode, schemaContext))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while checking if data at path " + pathToNode + " exist.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        }
        finally {
            domTransactionChain.close();
        }
    }

    /**
     * Submit wrote data in transaction.
     *
     * @param readWriteTransaction
     *            - transaction with data
     */
    public static void submitData(final DOMDataTreeReadWriteTransaction readWriteTransaction,
                                  final DOMTransactionChain domTransactionChain) {
        try {
            readWriteTransaction.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while submitting data to datastore.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        }
        finally {
            domTransactionChain.close();
        }
    }

    /**
     * Resolve transaction of mount point.
     *
     * @param mountPoint
     *            - to resolve transaction
     * @return resolved transaction chain handler
     */
    public static TransactionChainHandler resolveMountPointTransaction(final DOMMountPoint mountPoint) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return new TransactionChainHandler(domDataBrokerService.get());
        }

        final String errMsg = "DOM data broker service isn't available for mount point " + mountPoint.getIdentifier();
        throw new RestconfDocumentedException(errMsg,
                RestconfError.ErrorType.APPLICATION,
                RestconfError.ErrorTag.OPERATION_FAILED);
    }

    @SuppressWarnings("rawtypes")
    public static NormalizedNode mapYangNotificationStreamByIetfRestconfMonitoring(
            final NotificationDefinition notificationDefinition, final String prefixedStreamName,
            final Instant replayLogCreationTime, final URI uri, final Module monitoringModule,
            final boolean existParent) {
        final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                .findDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME).get())
                .findDataChildByName(MonitoringModule.CONT_STREAMS_QNAME).get())
                .findDataChildByName(MonitoringModule.LIST_STREAM_QNAME).get();
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry = Builders
                .mapEntryBuilder((ListSchemaNode) streamListSchema);

        final ListSchemaNode listSchema = (ListSchemaNode) streamListSchema;
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.findDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME).get(),
                prefixedStreamName);
        if (notificationDefinition.getDescription().isPresent()
                && !notificationDefinition.getDescription().get().isEmpty()) {
            prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.findDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME).get(),
                    notificationDefinition.getDescription().get());
        }
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.findDataChildByName(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME).get(), true);
        if (replayLogCreationTime != null) {
            prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.findDataChildByName(MonitoringModule.LEAF_START_TIME_STREAM_QNAME).get(),
                    timeStampToRFC3339Format(replayLogCreationTime));
        }
        prepareListAndFillEntryBuilder(streamEntry,
                (ListSchemaNode) listSchema.findDataChildByName(MonitoringModule.LIST_ACCESS_STREAM_QNAME).get(), uri);

        if (!existParent) {
            final DataSchemaNode contStreamsSchema = ((ContainerSchemaNode) monitoringModule
                    .findDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME).get())
                    .findDataChildByName(MonitoringModule.CONT_STREAMS_QNAME).get();
            return Builders.containerBuilder((ContainerSchemaNode) contStreamsSchema).withChild(Builders
                    .mapBuilder((ListSchemaNode) streamListSchema).withChild(streamEntry.build()).build())
                    .build();
        }
        return streamEntry.build();
    }

    private static void prepareListAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final ListSchemaNode listSchemaNode, final URI streamLocationUri) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> accessListBuilder = Builders.mapBuilder(listSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> accessListXmlEntry = Builders
                .mapEntryBuilder(listSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> accessListJsonEntry = Builders
                .mapEntryBuilder(listSchemaNode);

        prepareLeafAndFillEntryBuilder(accessListXmlEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME).get(), "xml");
        prepareLeafAndFillEntryBuilder(accessListXmlEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME).get(),
                streamLocationUri.toString());

        prepareLeafAndFillEntryBuilder(accessListJsonEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME).get(), "json");
        prepareLeafAndFillEntryBuilder(accessListJsonEntry,
                listSchemaNode.findDataChildByName(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME).get(),
                streamLocationUri.toString());

        streamEntry.withChild(accessListBuilder.withChild(accessListXmlEntry.build())
                .withChild(accessListJsonEntry.build())
                .build());
    }

    private static void prepareLeafAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final DataSchemaNode leafSchema,
            final Object value) {
        streamEntry.withChild(Builders.leafBuilder((LeafSchemaNode) leafSchema).withValue(value).build());
    }

    public static NormalizedNode<?, ?> createSubscriptionListEntryNode(
            final Uint32 subscriptionId,
            final Encoding encoding,
            final String stream,
            final Instant replayStartTime,
            final Instant stopTime,
            final Uint32 period,
            final String receiverSessionId,
            final Module subscribedNotificationsModule) {
        final ContainerSchemaNode subscriptionsContainerSchemaNode = (ContainerSchemaNode) subscribedNotificationsModule
                .findDataChildByName(Subscriptions.QNAME).get();
        final ListSchemaNode subscriptionListSchemaNode = (ListSchemaNode) subscriptionsContainerSchemaNode
                .findDataChildByName(Subscription.QNAME).get();
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> subscriptionListEntryBuilder =
                Builders.mapEntryBuilder(subscriptionListSchemaNode);

        prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder, subscriptionListSchemaNode.findDataChildByName(
                SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID.getNodeType()).get(), subscriptionId);
        prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder, subscriptionListSchemaNode.findDataChildByName(
                SubscribedNotificationsModuleUtils.ENCODING_LEAF_ID.getNodeType()).get(),
                QName.create(SubscribedNotificationsModuleUtils.ENCODING_LEAF_ID.getNodeType(), encoding.getKeyword()));
        if (stopTime != null) {
            prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder,
                    subscriptionListSchemaNode.findDataChildByName(
                            SubscribedNotificationsModuleUtils.STOP_TIME_LEAF_ID.getNodeType()).get(),
                    timeStampToRFC3339Format(stopTime));
        }

        final DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> targetChoiceNodeBuilder = Builders.choiceBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID);
        final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder =
                Builders.augmentationBuilder().withNodeIdentifier(SubscribedNotificationsModuleUtils.AUGMENTATION_ID);

        prepareLeafAndFillAugmentationBuilder(augmentationBuilder, SubscribedNotificationsModuleUtils.STREAM_LEAF_ID,
                stream);

        if (replayStartTime != null) {
            prepareLeafAndFillAugmentationBuilder(augmentationBuilder,
                    SubscribedNotificationsModuleUtils.REPLAY_START_TIME_LEAF_ID, timeStampToRFC3339Format(
                            replayStartTime));
        }

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> receivers = Builders
                .containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.RECEIVERS_CONTAINER_ID);
        final CollectionNodeBuilder<MapEntryNode, MapNode> receiver = Builders
                .mapBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.RECEIVER_LIST_ID);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> receiverEntry = Builders
                .mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Receiver.QNAME,
                        SubscribedNotificationsModuleUtils.RECEIVER_NAME,
                        receiverSessionId))
                .withChild(Builders.leafBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(SubscribedNotificationsModuleUtils.RECEIVER_STATE))
                        .withValue(State.Active.getName())
                        .build()
                );

        subscriptionListEntryBuilder.withChild(
                receivers.withChild(receiver.withChild(receiverEntry.build()).build()).build()
        );

        subscriptionListEntryBuilder.withChild(targetChoiceNodeBuilder.withChild(augmentationBuilder.build()).build());

        if (period != null) {
            final AugmentationNode augmentationNodeWithPeriodLeafData = createPeriodLeafNode(period);
            subscriptionListEntryBuilder.withChild(augmentationNodeWithPeriodLeafData);
        }

        return Builders.containerBuilder(subscriptionsContainerSchemaNode)
                .withChild(Builders.mapBuilder(subscriptionListSchemaNode).withChild(
                        subscriptionListEntryBuilder.build()).build()).build();
    }

    private static void prepareLeafAndFillAugmentationBuilder(
            final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode>
            augmentationBuilder, final NodeIdentifier leafIdentifier,
            final Object value) {
        augmentationBuilder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(leafIdentifier)
                .withValue(value)
                .build());
    }

    public static AugmentationNode createPeriodLeafNode(final Uint32 period) {
        final AugmentationIdentifier augmentationId = new AugmentationIdentifier(Sets.newHashSet(UpdateTrigger.QNAME));
        return Builders.augmentationBuilder()
                .withNodeIdentifier(augmentationId)
                .withChild(Builders.choiceBuilder()
                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.UPDATE_TRIGGER_CHOICE_ID)
                        .withChild(Builders.containerBuilder()
                                .withNodeIdentifier(SubscribedNotificationsModuleUtils.PERIODIC_CASE_ID)
                                .withChild(Builders.leafBuilder()
                                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.PERIOD_LEAF_ID)
                                        .withValue(period)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    public static String createJsonResponseBody(final NormalizedNode<?, ?> data, final SchemaPath pathToData,
            final SchemaContext schemaContext) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final OutputStreamWriter outputWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

        final JsonWriter jsonWriter = JsonWriterFactory.createJsonWriter(outputWriter);
        final NormalizedNodeStreamWriter jsonStreamWriter = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                JSONCodecFactorySupplier.RFC7951.getShared(schemaContext), pathToData.getParent(), null, jsonWriter);

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(jsonStreamWriter);
        try {
            nnWriter.write(data);
            nnWriter.flush();
            outputWriter.flush();
        } catch (final IOException ex) {
            throw new IllegalStateException("Serialization of YANG data to JSON http response failed.", ex);
        }

        return outputStream.toString(StandardCharsets.UTF_8);
    }

    public static String createXmlResponseBody(final NormalizedNode<?, ?> data, final SchemaPath pathToData,
            final SchemaContext schemaContext) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (final FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }

        final NormalizedNodeStreamWriter xmlStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext, pathToData.getParent());
        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(xmlStreamWriter);

        try {
            nnWriter.write(data);
            nnWriter.flush();
        } catch (final IOException ex) {
            throw new IllegalStateException("Serialization of YANG data to XML http response failed.", ex);
        }

        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
