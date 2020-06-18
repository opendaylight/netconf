/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8639.layer.services.impl.SubscribedNotificationsImpl;
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

public class RFC8639RestconfApp extends Application {
    private static final DOMRpcIdentifier RPC_ESTABLISH = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(EstablishSubscriptionInput.QNAME.getModule(), "establish-subscription")));
    private static final DOMRpcIdentifier RPC_MODIFY = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(ModifySubscriptionInput.QNAME.getModule(), "modify-subscription")));
    private static final DOMRpcIdentifier RPC_DELETE = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(DeleteSubscriptionInput.QNAME.getModule(), "delete-subscription")));
    private static final DOMRpcIdentifier RPC_KILL = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(KillSubscriptionInput.QNAME.getModule(), "kill-subscription")));

    private static final NotificationsHolder NOTIFICATIONS_HOLDER = new NotificationsHolder(new Random());

    private final TransactionChainHandler transactionChainHandler;
    private final SchemaContextHandler schemaContextHandler;
    private final DOMNotificationService domNotificationService;
    private final DOMSchemaService domSchemaService;
    private final DOMMountPointService domMountPointService;
    private final DOMRpcProviderService domRpcProviderService;

    public RFC8639RestconfApp(
            final TransactionChainHandler transactionChainHandler,
            final SchemaContextHandler schemaContextHandler,
            final DOMNotificationService domNotificationService,
            final DOMSchemaService domSchemaService,
            final DOMMountPointService domMountPointService,
            final DOMRpcProviderService domRpcProviderService) {
        this.transactionChainHandler = transactionChainHandler;
        this.schemaContextHandler = schemaContextHandler;
        this.domNotificationService = domNotificationService;
        this.domSchemaService = domSchemaService;
        this.domMountPointService = domMountPointService;
        this.domRpcProviderService = domRpcProviderService;
    }

    public void init() {
        final Map<QName, ReplayBuffer> replayBuffersForNotifications = new HashMap<>();
        final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(
                Executors.newFixedThreadPool(1));

        final DOMRpcImplementation establishSubscriptionRpc = new EstablishSubscriptionRpc(NOTIFICATIONS_HOLDER,
                replayBuffersForNotifications, domNotificationService, domSchemaService,
                domMountPointService, transactionChainHandler, executorService);
        domRpcProviderService.registerRpcImplementation(establishSubscriptionRpc, RPC_ESTABLISH);

        final DOMRpcImplementation modifySubscriptionRpc = new ModifySubscriptionRpc(NOTIFICATIONS_HOLDER,
                domSchemaService, transactionChainHandler, executorService);
        domRpcProviderService.registerRpcImplementation(modifySubscriptionRpc, RPC_MODIFY);

        final DOMRpcImplementation deleteSubscriptionRpc = new RemoveSubscriptionRpc(NOTIFICATIONS_HOLDER,
                false, executorService);
        domRpcProviderService.registerRpcImplementation(deleteSubscriptionRpc, RPC_DELETE);

        final DOMRpcImplementation killSubscriptionRpc = new RemoveSubscriptionRpc(NOTIFICATIONS_HOLDER,
                true, executorService);
        domRpcProviderService.registerRpcImplementation(killSubscriptionRpc, RPC_KILL);

        final OnEffectiveModelContextChangeNotificationTracker onEffectiveModelContextChangeNotificationTracker =
                new OnEffectiveModelContextChangeNotificationTracker(domNotificationService,
                        transactionChainHandler,
                        replayBuffersForNotifications,
                        20000,
                        new InetSocketAddress(8181));
        domSchemaService.registerSchemaContextListener(onEffectiveModelContextChangeNotificationTracker);
    }

    @Override
    public Set<Object> getSingletons() {
        return ImmutableSet.builder()
                .add(new SubscribedNotificationsImpl(this.schemaContextHandler, NOTIFICATIONS_HOLDER))
                .build();
    }
}
