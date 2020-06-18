/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public final class EstablishSubscriptionRpc implements DOMRpcImplementation {
    private static final Logger LOG = LoggerFactory.getLogger(EstablishSubscriptionRpc.class);
    private static final NodeIdentifier OUTPUT = NodeIdentifier.create(EstablishSubscriptionOutput.QNAME);
    private static final QName CREATE_SUBSCRIPTION = QName.create(CreateSubscriptionInput.QNAME,
            "create-subscription").intern();
    private static final NodeIdentifier CREATE_SUBSCRIPTION_ID = NodeIdentifier.create(CREATE_SUBSCRIPTION);
    private static final QName STREAM = QName.create(CREATE_SUBSCRIPTION, "stream").intern();
    private static final NodeIdentifier STREAM_ID = NodeIdentifier.create(STREAM);

    private String getSessionId() {
        return "0";
    }

    private final SubscriptionsHolder subscriptionsHolder;
    private final Map<String, ReplayBuffer> replayBuffersForNotifications;
    private final DOMNotificationService domNotificationService;
    private final DOMMountPointService domMountPointService;
    private final TransactionChainHandler transactionChainHandler;
    private final DOMSchemaService domSchemaService;
    private final ListeningExecutorService executorService;
    private final DOMRpcService domRpcService;

    public EstablishSubscriptionRpc(final SubscriptionsHolder subscriptionsHolder,
            final Map<String, ReplayBuffer> replayBuffersForNotifications,
            final DOMNotificationService domNotificationService, final DOMSchemaService domSchemaService,
            final DOMMountPointService domMountPointService, final TransactionChainHandler transactionChainHandler,
            final ListeningExecutorService executorService, final DOMRpcService domRpcService) {
        this.subscriptionsHolder = requireNonNull(subscriptionsHolder);
        this.replayBuffersForNotifications = requireNonNull(replayBuffersForNotifications);
        this.domNotificationService = requireNonNull(domNotificationService);
        this.domSchemaService = requireNonNull(domSchemaService);
        this.domMountPointService = requireNonNull(domMountPointService);
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        this.executorService = requireNonNull(executorService);
        this.domRpcService = domRpcService;
    }

    @SuppressWarnings("serial")
    @Override
    public @NonNull FluentFuture<DOMRpcResult> invokeRpc(final @NonNull DOMRpcIdentifier rpc,
            final @NonNull NormalizedNode<?, ?> input) {
        final ListenableFuture<DOMRpcResult> futureWithDOMRpcResult = executorService.submit(() -> processRpc(input));
        return FluentFuture.from(futureWithDOMRpcResult);
    }

    @SuppressWarnings("unchecked")
    private DOMRpcResult processRpc(final NormalizedNode<?, ?> input) {
        final String rawStreamName = resolveStreamNameFromInput(input);
        final Optional<StreamWrapper> wrapperOpt = subscribeToStream(rawStreamName);

        final StreamWrapper wrapper;
        if (wrapperOpt.isPresent()) {
            wrapper = wrapperOpt.get();
        } else {
            LOG.error("Notification stream with requested name '{}' does not exist.", rawStreamName);
            return createErrorResponse(ErrorCode.STREAM_UNAVAILABLE);
        }

        final Encoding encoding;
        final Optional<NormalizedNode<?, ?>> encodingNodeOpt = getEncoding(input);
        if (encodingNodeOpt.isPresent()) {
            final String rawEncoding = ((LeafNode<QName>) encodingNodeOpt.get()).getValue().getLocalName();
            final Encoding parsed = Encoding.parse(rawEncoding);
            if (parsed == null) {
                LOG.warn("Invalid encoding parameter {}, falling back to encode-xml.", rawEncoding);
                encoding = Encoding.ENCODE_XML;
            } else {
                encoding = parsed;
            }
        } else {
            LOG.debug("Unable to read encoding from establish-subscription, using encode-xml.");
            encoding = Encoding.ENCODE_XML;
        }

        final Set<SchemaPath> notifications = wrapper.getNotifications();
        final NotificationStreamListener listener = new NotificationStreamListener(rawStreamName,
                wrapper.getEffectiveModelContext(), getSessionId(), transactionChainHandler, encoding);

        final ListenerRegistration<NotificationStreamListener> registration = wrapper.getDomNotificationService()
                .registerNotificationListener(listener, notifications);
        listener.setRegistration(registration);

        final RegisteredNotificationWrapper registeredNotificationWrapper = new RegisteredNotificationWrapper(listener,
                registration);

        final ReplayStartTimeRevision replayStartTimeRevision = evaluateReplay(input, rawStreamName, listener);
        if (replayStartTimeRevision == null) {
            return createErrorResponse(ErrorCode.REPLAY_UNSUPPORTED);
        }

        final Optional<NormalizedNode<?, ?>> periodOpt = SubscribedNotificationsModuleUtils.getPeriod(input);
        final Uint32 period;
        if (periodOpt.isPresent()) {
            period = ((LeafNode<Uint32>) periodOpt.get()).getValue();
            listener.setPeriod(period);
        } else {
            period = null;
            LOG.debug("Unable to read period from establish-subscription rpc input.");
        }

        final Optional<NormalizedNode<?, ?>> anchorTimeOpt = SubscribedNotificationsModuleUtils.getAnchorTime(input);
        if (anchorTimeOpt.isPresent()) {
            listener.setAnchorTime(((LeafNode<String>) anchorTimeOpt.get()).getValue());
        } else {
            LOG.debug("Unable to read anchor-time from establish-subscription rpc input.");
        }

        final Instant stopTime;
        final Optional<NormalizedNode<?, ?>> stopTimeOpt = SubscribedNotificationsModuleUtils.getStopTime(input);
        if (stopTimeOpt.isPresent()) {
            stopTime = Instant.parse(((LeafNode<String>) stopTimeOpt.get()).getValue());
            listener.setStopTime(stopTime);
        } else {
            stopTime = null;
            LOG.debug("Unable to read stop-time from establish-subscription rpc input.");
        }

        final Uint32 subscriptionId = subscriptionsHolder.registerNotification(registeredNotificationWrapper);
        listener.setSubscriptionId(subscriptionId);
        writeStreamSubscriptionToDatastore(subscriptionId, encoding, rawStreamName,
                replayStartTimeRevision.replayStartTime, stopTime, period);

        if (replayStartTimeRevision.isRevisited) {
            return createSuccessResponse(subscriptionId, replayStartTimeRevision.replayStartTime);
        } else {
            return createSuccessResponse(subscriptionId);
        }
    }

    /**
     * Evaluate replay start time from input.
     *
     * @return {@link ReplayStartTimeRevision} or {@code null} if replay is not supported
     */
    private ReplayStartTimeRevision evaluateReplay(final NormalizedNode<?, ?> input,
            final String stream, final NotificationStreamListener listener) {
        final Optional<NormalizedNode<?, ?>> replayStartTimeOpt = getReplayStartTime(input);
        if (replayStartTimeOpt.isPresent()) {
            // yangtools json/xml codec ensures that the String value of replay-start-time conforms to the regex
            // pattern of date-and-time type
            final String rawReplayStartTime = ((LeafNode<String>) replayStartTimeOpt.get()).getValue();
            final Instant replayStartTime = Instant.parse(rawReplayStartTime);
            final ReplayBuffer replayBufferForStream = replayBuffersForNotifications.get(stream);

            if (replayBufferForStream == null) {
                LOG.info("Replay buffer for notification stream {} does not exist.", stream);
                return null;
            } else if (replayBufferForStream.isEmpty()) {
                LOG.info("Replay buffer for notification stream {} is empty.", stream);
                return null;
            } else {
                final Instant oldestNotificationTimeStamp = replayBufferForStream.getOldestNotificationTimeStamp();
                if (replayStartTime.isBefore(oldestNotificationTimeStamp)) {
                    LOG.error("Parameter replay-start-time ({}) is earlier than the oldest record stored within the "
                                    + "publisher's replay buffer for the notification {}."
                                    + "The oldest record in the buffer is {}.",
                            replayStartTime, stream, oldestNotificationTimeStamp);
                    listener.setReplayStartTime(oldestNotificationTimeStamp);
                    listener.setReplayBufferForStream(replayBufferForStream);
                    LOG.info("Parameter replay-start-time for notification stream {} was set to: {}.",
                            stream, oldestNotificationTimeStamp);

                    return new ReplayStartTimeRevision(oldestNotificationTimeStamp, true);
                } else {
                    listener.setReplayStartTime(replayStartTime);
                    listener.setReplayBufferForStream(replayBufferForStream);
                    LOG.info("Parameter replay-start-time for notification stream {} was set to: {}.",
                            stream, replayStartTime);

                    return new ReplayStartTimeRevision(replayStartTime, false);
                }
            }
        } else {
            LOG.debug("Unable to read replay-start-time from establish-subscription.");
            return new ReplayStartTimeRevision(null, false);
        }
    }

    private static class ReplayStartTimeRevision {
        private final Instant replayStartTime;
        private final boolean isRevisited;

        ReplayStartTimeRevision(final Instant replayStartTime, final boolean isRevisited) {
            this.replayStartTime = replayStartTime;
            this.isRevisited = isRevisited;
        }
    }

    private DefaultDOMRpcResult createErrorResponse(final ErrorCode reason) {
        final RpcError rpcError = RpcResultBuilder.newError(ErrorType.PROTOCOL,
                ErrorTag.OPERATION_FAILED.getTagValue(), reason.name());
        return new DefaultDOMRpcResult(rpcError);
    }

    private static DefaultDOMRpcResult createSuccessResponse(final Uint32 subscriptionId) {
        return new DefaultDOMRpcResult(Builders.containerBuilder()
                .withNodeIdentifier(OUTPUT)
                .withChild(Builders.leafBuilder()
                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID)
                        .withValue(subscriptionId)
                        .build())
                .build());
    }

    private static DefaultDOMRpcResult createSuccessResponse(final Uint32 subscriptionId,
            final Instant replayStartTimeRevision) {
        return new DefaultDOMRpcResult(Builders.containerBuilder()
                .withNodeIdentifier(OUTPUT)
                .withChild(Builders.leafBuilder()
                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID)
                        .withValue(subscriptionId)
                        .build())
                .withChild(Builders.leafBuilder()
                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.REPLAY_START_TIME_REVISION_ID)
                        .withValue(SubscribedNotificationsUtil.timeStampToRFC3339Format(replayStartTimeRevision))
                        .build())
                .build());
    }

    private static Optional<NormalizedNode<?, ?>> getEncoding(final NormalizedNode<?, ?> input) {
        return NormalizedNodes.findNode(input, SubscribedNotificationsModuleUtils.ENCODING_LEAF_ID);
    }

    private static Optional<NormalizedNode<?, ?>> getReplayStartTime(final NormalizedNode<?, ?> input) {
        return NormalizedNodes.findNode(input,
                SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID,
                SubscribedNotificationsModuleUtils.AUGMENTATION_ID,
                SubscribedNotificationsModuleUtils.REPLAY_START_TIME_LEAF_ID);
    }

    private void writeStreamSubscriptionToDatastore(final Uint32 subscriptionId, final Encoding encoding,
            final String streamName, final Instant replayStartTime, final Instant stopTime, final Uint32 period) {
        final EffectiveModelContext globalContext = domSchemaService.getGlobalContext();
        final Module subscribedNotificationsModule = globalContext.findModule(
                SubscribedNotificationsModuleUtils.SUBSCRIBED_MODULE).get();
        final NormalizedNode<?, ?> subscriptionData = createSubscriptionListEntryNode(subscriptionId,
                encoding, streamName, replayStartTime, stopTime, period, getSessionId(), subscribedNotificationsModule);

        final DOMTransactionChain domTransactionChain = transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction wTx = domTransactionChain.newReadWriteTransaction();
        wTx.merge(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.of(subscriptionData.getNodeType()), subscriptionData);
        SubscribedNotificationsUtil.submitData(wTx, domTransactionChain);
    }

    private static NormalizedNode<?, ?> createSubscriptionListEntryNode(final Uint32 subscriptionId,
            final Encoding encoding, final String stream, final Instant replayStartTime, final Instant stopTime,
            final Uint32 period, final String receiverSessionId, final Module subscribedNotificationsModule) {
        final ContainerSchemaNode subscriptionsContainerSchemaNode = (ContainerSchemaNode) subscribedNotificationsModule
                .findDataChildByName(Subscriptions.QNAME).get();
        final ListSchemaNode subscriptionListSchemaNode = (ListSchemaNode) subscriptionsContainerSchemaNode
                .findDataChildByName(Subscription.QNAME).get();
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> subscriptionListEntryBuilder =
                Builders.mapEntryBuilder(subscriptionListSchemaNode);

        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder,
                subscriptionListSchemaNode.findDataChildByName(
                        SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID.getNodeType()).get(), subscriptionId);
        SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder,
                subscriptionListSchemaNode.findDataChildByName(
                        SubscribedNotificationsModuleUtils.ENCODING_LEAF_ID.getNodeType()).get(),
                QName.create(SubscribedNotificationsModuleUtils.ENCODING_LEAF_ID.getNodeType(), encoding.getKeyword()));
        if (stopTime != null) {
            SubscribedNotificationsUtil.prepareLeafAndFillEntryBuilder(subscriptionListEntryBuilder,
                    subscriptionListSchemaNode.findDataChildByName(SubscribedNotificationsModuleUtils
                            .STOP_TIME_LEAF_ID.getNodeType()).get(),
                    SubscribedNotificationsUtil.timeStampToRFC3339Format(stopTime));
        }

        final DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> targetChoiceNodeBuilder = Builders.choiceBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID);
        final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder =
                Builders.augmentationBuilder().withNodeIdentifier(SubscribedNotificationsModuleUtils.AUGMENTATION_ID);

        prepareLeafAndFillAugmentationBuilder(augmentationBuilder,
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID, stream);

        if (replayStartTime != null) {
            prepareLeafAndFillAugmentationBuilder(augmentationBuilder,
                    SubscribedNotificationsModuleUtils.REPLAY_START_TIME_LEAF_ID,
                    SubscribedNotificationsUtil.timeStampToRFC3339Format(replayStartTime));
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
                        .build());

        subscriptionListEntryBuilder.withChild(receivers
                .withChild(receiver.withChild(receiverEntry.build()).build()).build());

        subscriptionListEntryBuilder.withChild(targetChoiceNodeBuilder
                .withChild(augmentationBuilder.build()).build());

        if (period != null) {
            final AugmentationNode augmentationNodeWithPeriodLeafData = SubscribedNotificationsUtil
                    .createPeriodLeafNode(period);
            subscriptionListEntryBuilder.withChild(augmentationNodeWithPeriodLeafData);
        }

        return Builders.containerBuilder(subscriptionsContainerSchemaNode)
                .withChild(Builders.mapBuilder(subscriptionListSchemaNode).withChild(
                        subscriptionListEntryBuilder.build()).build()).build();
    }

    private static void prepareLeafAndFillAugmentationBuilder(
            final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder,
            final NodeIdentifier leafIdentifier, final Object value) {
        augmentationBuilder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(leafIdentifier)
                .withValue(value)
                .build());
    }

    private static String resolveStreamNameFromInput(final NormalizedNode<?, ?> input) {
        // TODO default is NETCONF
        return (String) NormalizedNodes.findNode(input,
                SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID,
                SubscribedNotificationsModuleUtils.AUGMENTATION_ID,
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID).get().getValue();
    }

    private Optional<StreamWrapper> subscribeToStream(final String rawStreamName) {
        if (rawStreamName.contains("yang-ext:mount")) {
            final String streamName = rawStreamName.split("yang-ext:mount/")[1];
            final String topologySubstring = rawStreamName.substring(rawStreamName.indexOf("topology=") + 9);
            final String topologyId = topologySubstring.substring(0, topologySubstring.indexOf('/'));
            final String nodeSubstring = rawStreamName.substring(rawStreamName.indexOf("node=") + 5);
            final String nodeId = nodeSubstring.substring(0, topologySubstring.indexOf('/'));
            final YangInstanceIdentifier topologyNodeYIID = createTopologyNodeYIID(topologyId, nodeId);
            final Optional<DOMMountPoint> mountPointOpt = domMountPointService.getMountPoint(topologyNodeYIID);

            if (mountPointOpt.isEmpty()) {
                LOG.error("Mount point {} in {} does not exist.", topologyId, nodeId);
                return Optional.empty();
            }

            final DOMMountPoint domMountPoint = mountPointOpt.get();
            final Optional<DOMNotificationService> notificationServiceOpt = domMountPoint
                    .getService(DOMNotificationService.class);
            if (notificationServiceOpt.isEmpty()) {
                LOG.error("Missing DOMNotificationService for mount point {}.", topologyNodeYIID);
                return Optional.empty();
            }

            final Optional<DOMRpcService> rpcServiceOptional = domMountPoint.getService(DOMRpcService.class);
            if (rpcServiceOptional.isEmpty()) {
                LOG.error("Missing DOMRpcService for mount point {}.", topologyNodeYIID);
                return Optional.empty();
            }

            if (!subscribe(streamName, rpcServiceOptional.get())) {
                return Optional.empty();
            }

            return Optional.of(new StreamWrapper(
                    domMountPoint.getEffectiveModelContext(), notificationServiceOpt.get()));
        }

        return Optional.of(new StreamWrapper(domSchemaService.getGlobalContext(), domNotificationService));
    }

    private boolean subscribe(final String streamName, final DOMRpcService service) {
        final DataContainerChild<?, ?> stream = Builders.leafBuilder()
                .withNodeIdentifier(STREAM_ID).withValue(streamName).build();

        final ContainerNode rpcInput = Builders.containerBuilder()
                .withNodeIdentifier(CREATE_SUBSCRIPTION_ID).withChild(stream).build();

        final DOMRpcResult rpcResult;
        try {
            rpcResult = service.invokeRpc(SchemaPath.create(true, CREATE_SUBSCRIPTION), rpcInput).get();
        } catch (final InterruptedException | ExecutionException e) {
            LOG.error("Create subscription call to {} stream was not successful.", streamName);
            return false;
        }

        if (rpcResult.getErrors().isEmpty()) {
            LOG.info("Create subscription call to {} stream was successful.", streamName);
            return true;
        } else {
            LOG.error("Create subscription call to {} stream was not successful.", streamName);
            return false;
        }
    }

    private static YangInstanceIdentifier createTopologyNodeYIID(final String topologyId, final String nodeId) {
        return YangInstanceIdentifier.builder()
                .node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), topologyId)
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), nodeId)
                .build();
    }

    @VisibleForTesting
    public static final class StreamWrapper {
        private final EffectiveModelContext effectiveModelContext;
        private final DOMNotificationService domNotificationService;

        public StreamWrapper(final EffectiveModelContext effectiveModelContext,
                final DOMNotificationService domNotificationService) {
            this.effectiveModelContext = effectiveModelContext;
            this.domNotificationService = domNotificationService;
        }

        public EffectiveModelContext getEffectiveModelContext() {
            return effectiveModelContext;
        }

        public Set<SchemaPath> getNotifications() {
            return effectiveModelContext.getNotifications().stream()
                    .map(SchemaNode::getPath)
                    .collect(Collectors.toSet());
        }

        public DOMNotificationService getDomNotificationService() {
            return domNotificationService;
        }
    }
}
