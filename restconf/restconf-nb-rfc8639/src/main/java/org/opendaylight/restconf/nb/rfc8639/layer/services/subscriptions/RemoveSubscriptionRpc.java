/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.ServletInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RemoveSubscriptionRpc implements DOMRpcImplementation {
    private static final Logger LOG = LoggerFactory.getLogger(RemoveSubscriptionRpc.class);
    private static final NodeIdentifier OUTPUT = NodeIdentifier.create(DeleteSubscriptionOutput.QNAME);

    private final NotificationsHolder notificationsHolder;
    private final ServletInfo servletInfo;
    private final ListeningExecutorService executorService;
    private final boolean isKill;

    public RemoveSubscriptionRpc(final NotificationsHolder notificationsHolder, final ServletInfo servletInfo,
            final boolean isKill, final ListeningExecutorService executorService) {
        this.notificationsHolder = notificationsHolder;
        this.servletInfo = servletInfo;
        this.executorService = executorService;
        this.isKill = isKill;
    }

    @Override
    public @NonNull FluentFuture<DOMRpcResult> invokeRpc(final DOMRpcIdentifier rpc,
                                                         final NormalizedNode<?, ?> input) {
        final ListenableFuture<DOMRpcResult> futureWithDOMRpcResult = executorService.submit(() -> processRpc(input));
        return FluentFuture.from(futureWithDOMRpcResult);
    }

    private DOMRpcResult processRpc(final NormalizedNode<?, ?> input) {
        LOG.debug("Delete subscription rpc invoked");
        final Optional<NormalizedNode<?, ?>> optOfNode = SubscribedNotificationsModuleUtils.getIdentifier(input);
        if (!optOfNode.isPresent()) {
            LOG.error("Unable to read identifier from delete-subscription input.");
            return createErrorResponse(ErrorCode.NO_SUCH_SUBSCRIPTION.getQName());
        }

        final Uint32 subscriptionId = ((LeafNode<Uint32>) optOfNode.get()).getValue();
        final RegisteredNotificationWrapper notificationWrapper = this.notificationsHolder
                .getNotification(subscriptionId);
        if (notificationWrapper == null) {
            LOG.error("Subscription with requested id {} has not been found.", subscriptionId);
            return createErrorResponse(ErrorCode.NO_SUCH_SUBSCRIPTION.getQName());
        }

        final NotificationStreamListener listener = notificationWrapper.getSubscriptionNotificationListener();
        if (listener.deleteSubscription(this.servletInfo.getSessionId(), this.isKill)) {
            this.notificationsHolder.removeNotification(subscriptionId);

            final NotificationDefinition notificationDefinition = listener.getNotificationDefinition();
            LOG.info("Subscription with id {} to notification stream {} has been deleted successfully.", subscriptionId,
                    notificationDefinition.getQName());
            return createSuccessResponse();
        } else {
            LOG.info("Subscription can not be deleted by a subscriber who did not establish it.");
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
}
