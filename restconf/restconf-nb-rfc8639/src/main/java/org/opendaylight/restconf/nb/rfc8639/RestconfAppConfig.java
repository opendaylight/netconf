/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8639.handlers.TxChainHandler;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.EstablishSubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.ModifySubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.OnEffectiveModelContextChangeNotificationTracker;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RemoveSubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.ReplayBuffer;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscriptionIdGenerator.Random;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.KillSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@Singleton
public class RestconfAppConfig {
    private static final DOMRpcIdentifier RPC_ESTABLISH = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(EstablishSubscriptionInput.QNAME.getModule(), "establish-subscription")));
    private static final DOMRpcIdentifier RPC_MODIFY = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(ModifySubscriptionInput.QNAME.getModule(), "modify-subscription")));
    private static final DOMRpcIdentifier RPC_DELETE = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(DeleteSubscriptionInput.QNAME.getModule(), "delete-subscription")));
    private static final DOMRpcIdentifier RPC_KILL = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(KillSubscriptionInput.QNAME.getModule(), "kill-subscription")));

    @Inject
    public RestconfAppConfig(
            final TxChainHandler transactionChainHandler,
            final @Reference DOMNotificationService domNotificationService,
            final @Reference DOMSchemaService domSchemaService,
            final @Reference DOMMountPointService domMountPointService,
            final @Reference DOMRpcProviderService domRpcProviderService) {
        final NotificationsHolder notificationsHolder = new NotificationsHolder(new Random());
        final Map<QName, ReplayBuffer> replayBuffersForNotifications = new HashMap<>();
        final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(
                Executors.newFixedThreadPool(1));

        final DOMRpcImplementation establishSubscriptionRpc = new EstablishSubscriptionRpc(notificationsHolder,
                replayBuffersForNotifications, domNotificationService, domSchemaService,
                domMountPointService, transactionChainHandler, executorService);
        domRpcProviderService.registerRpcImplementation(establishSubscriptionRpc, RPC_ESTABLISH);

        final DOMRpcImplementation modifySubscriptionRpc = new ModifySubscriptionRpc(notificationsHolder,
                domSchemaService, transactionChainHandler, executorService);
        domRpcProviderService.registerRpcImplementation(modifySubscriptionRpc, RPC_MODIFY);

        final DOMRpcImplementation deleteSubscriptionRpc = new RemoveSubscriptionRpc(notificationsHolder,
                false, executorService);
        domRpcProviderService.registerRpcImplementation(deleteSubscriptionRpc, RPC_DELETE);

        final DOMRpcImplementation killSubscriptionRpc = new RemoveSubscriptionRpc(notificationsHolder,
                true, executorService);
        domRpcProviderService.registerRpcImplementation(killSubscriptionRpc, RPC_KILL);

        final OnEffectiveModelContextChangeNotificationTracker onEffectiveModelContextChangeNotificationTracker =
                new OnEffectiveModelContextChangeNotificationTracker(domNotificationService,
                        transactionChainHandler,
                        replayBuffersForNotifications,
                        20000,
                        new InetSocketAddress("localhost", 8181));
        domSchemaService.registerSchemaContextListener(onEffectiveModelContextChangeNotificationTracker);
    }
}
