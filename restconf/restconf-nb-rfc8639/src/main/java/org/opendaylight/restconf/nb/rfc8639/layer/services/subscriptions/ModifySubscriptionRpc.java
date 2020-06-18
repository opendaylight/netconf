/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.ServletInfo;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModifySubscriptionRpc implements DOMRpcImplementation {
    private static final Logger LOG = LoggerFactory.getLogger(ModifySubscriptionRpc.class);
    private static final NodeIdentifier OUTPUT = NodeIdentifier.create(ModifySubscriptionOutput.QNAME);

    private final NotificationsHolder notificationsHolder;
    private final DOMSchemaService domSchemaService;
    private final TransactionChainHandler transactionChainHandler;
    private final ServletInfo servletInfo;
    private final ListeningExecutorService executorService;

    public ModifySubscriptionRpc(final NotificationsHolder notificationsHolder,
            final DOMSchemaService domSchemaService, final TransactionChainHandler transactionChainHandler,
            final ServletInfo servletInfo, final ListeningExecutorService executorService) {
        this.notificationsHolder = notificationsHolder;
        this.domSchemaService = domSchemaService;
        this.transactionChainHandler = transactionChainHandler;
        this.servletInfo = servletInfo;
        this.executorService = executorService;
    }

    @Override
    public @NonNull FluentFuture<DOMRpcResult> invokeRpc(final DOMRpcIdentifier rpc,
            final NormalizedNode<?, ?> input) {
        final ListenableFuture<DOMRpcResult> futureWithDOMRpcResult = executorService.submit(() -> processRpc(input));
        return FluentFuture.from(futureWithDOMRpcResult);
    }

    private DOMRpcResult processRpc(final NormalizedNode<?, ?> input) {
        final Optional<NormalizedNode<?, ?>> subscriptionIdOpt = SubscribedNotificationsModuleUtils
                .getIdentifier(input);
        if (!subscriptionIdOpt.isPresent()) {
            LOG.error("Unable to read identifier from modify-subscription rpc input.");
            return createErrorResponse(ErrorCode.NO_SUCH_SUBSCRIPTION.getQName());
        }

        final Uint32 subscriptionId = ((LeafNode<Uint32>) subscriptionIdOpt.get()).getValue();
        final RegisteredNotificationWrapper notificationWrapper = this.notificationsHolder
                .getNotification(subscriptionId);
        if (notificationWrapper == null) {
            LOG.error("Subscription with requested id {} has not been found.", subscriptionId);
            return createErrorResponse(ErrorCode.NO_SUCH_SUBSCRIPTION.getQName());
        }

        final NotificationStreamListener listener = notificationWrapper.getSubscriptionNotificationListener();

        if (listener.hasEqualSessionId(servletInfo.getSessionId())) {
            final Optional<NormalizedNode<?, ?>> stopTimeOpt = SubscribedNotificationsModuleUtils.getStopTime(input);
            if (stopTimeOpt.isPresent()) {
                final Instant stopTime = Instant.parse(((LeafNode<String>) stopTimeOpt.get()).getValue());
                updateSubscriptionStopTimeInDatastore(subscriptionId, stopTime);
                listener.setStopTime(stopTime);
            } else {
                LOG.debug("Unable to read stop-time from modify-subscription rpc input.");
            }

            final Optional<NormalizedNode<?, ?>> periodOpt = SubscribedNotificationsModuleUtils.getPeriod(input);
            if (periodOpt.isPresent()) {
                final Uint32 period = ((LeafNode<Uint32>) periodOpt.get()).getValue();
                updateSubscriptionPeriodInDatastore(subscriptionId, period);
                listener.setPeriod(period);
            } else {
                LOG.debug("Unable to read period from modify-subscription rpc input.");
            }

            final Optional<NormalizedNode<?, ?>> anchorTimeOpt = SubscribedNotificationsModuleUtils
                    .getAnchorTime(input);
            if (anchorTimeOpt.isPresent()) {
                listener.setAnchorTime(((LeafNode<DateAndTime>) anchorTimeOpt.get()).getValue().getValue());
            } else {
                LOG.debug("Unable to read anchor-time from modify-subscription rpc input.");
            }

            listener.scheduleNotifications();

            LOG.info("Subscription {} has been successfully modified", subscriptionId);
            return createSuccessResponse();
        } else {
            LOG.error("Subscription can not be modified by a subscriber who did not establish it.");
            return createErrorResponse(ErrorCode.NO_SUCH_SUBSCRIPTION.getQName());
        }
    }

    private static DefaultDOMRpcResult createSuccessResponse() {
        return new DefaultDOMRpcResult(Builders.containerBuilder()
                .withNodeIdentifier(OUTPUT)
                .build());
    }

    private static DefaultDOMRpcResult createErrorResponse(final QName reason) {
        final RpcError rpcError = RpcResultBuilder.newError(
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_FAILED.getTagValue(),
                reason.getLocalName()
        );
        return new DefaultDOMRpcResult(rpcError);
    }

    private void updateSubscriptionStopTimeInDatastore(final Uint32 subscriptionId, final Instant stopTime) {
        final String pathToStopTimeLeafNode = SubscribedNotificationsModuleUtils.PATH_TO_SUBSCRIPTION_WITHOUT_KEY
                + subscriptionId
                + "/" + SubscribedNotificationsModuleUtils.STOP_TIME_LEAF_ID.getNodeType().getLocalName();

        final LeafNode<String> stopTimeLeafData = Builders.<String>leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.STOP_TIME_LEAF_ID).withValue(DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(OffsetDateTime.ofInstant(stopTime, ZoneId.systemDefault()))).build();
        final DOMTransactionChain domTransactionChain = this.transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction transaction = domTransactionChain.newReadWriteTransaction();
        transaction.put(LogicalDatastoreType.OPERATIONAL, IdentifierCodec.deserialize(
                pathToStopTimeLeafNode, domSchemaService.getGlobalContext()), stopTimeLeafData);
        SubscribedNotificationsUtil.submitData(transaction, domTransactionChain);
    }

    private void updateSubscriptionPeriodInDatastore(final Uint32 subscriptionId, final Uint32 period) {
        final String pathToPeriodLeafNode = SubscribedNotificationsModuleUtils.PATH_TO_SUBSCRIPTION_WITHOUT_KEY
                + subscriptionId;

        final NodeIdentifierWithPredicates subscriptionListEntryId = NodeIdentifierWithPredicates.of(
                Subscription.QNAME, IDENTIFIER_LEAF_ID.getNodeType(), subscriptionId);
        final MapEntryNode subscriptionListEntryNode = Builders.mapEntryBuilder().withNodeIdentifier(
                subscriptionListEntryId).withChild(SubscribedNotificationsUtil.createPeriodLeafNode(period)).build();
        final DOMTransactionChain domTransactionChain = this.transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction transaction = domTransactionChain.newReadWriteTransaction();
        transaction.merge(LogicalDatastoreType.OPERATIONAL, IdentifierCodec.deserialize(
                pathToPeriodLeafNode, domSchemaService.getGlobalContext()), subscriptionListEntryNode);
        SubscribedNotificationsUtil.submitData(transaction, domTransactionChain);
    }
}
