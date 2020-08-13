/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementation;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8639.layer.services.impl.SubscribedNotificationsImpl;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.EstablishSubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.ModifySubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RemoveSubscriptionRpc;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.ReplayBuffer;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscriptionIdGenerator.Random;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscriptionsHolder;
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

    // storage for all subscriptions
    private static final SubscriptionsHolder SUBSCRIPTIONS_HOLDER = new SubscriptionsHolder(new Random());

    // replay buffer
    private static final Map<String, ReplayBuffer> REPLAY_BUFFER = new ConcurrentHashMap<>();
    public static final int BUFFER_MAX_SIZE = 10_000;

    private final TransactionChainHandler transactionChainHandler;
    private final DOMNotificationService domNotificationService;
    private final DOMSchemaService domSchemaService;
    private final DOMMountPointService domMountPointService;
    private final DOMRpcProviderService domRpcProviderService;
    private final RpcServiceHandler domRpcServiceHandler;

    public RFC8639RestconfApp(final TransactionChainHandler transactionChainHandler,
            final DOMNotificationService domNotificationService, final DOMSchemaService domSchemaService,
            final DOMMountPointService domMountPointService, final DOMRpcProviderService domRpcProviderService,
            final RpcServiceHandler domRpcServiceHandler) {
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        this.domNotificationService = requireNonNull(domNotificationService);
        this.domSchemaService = requireNonNull(domSchemaService);
        this.domMountPointService = requireNonNull(domMountPointService);
        this.domRpcProviderService = requireNonNull(domRpcProviderService);
        this.domRpcServiceHandler = domRpcServiceHandler;
    }

    public void init() {
        final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(
                Executors.newFixedThreadPool(1));

        final DOMRpcImplementation establishSubscriptionRpc = new EstablishSubscriptionRpc(SUBSCRIPTIONS_HOLDER,
                REPLAY_BUFFER, domNotificationService, domSchemaService, domMountPointService, transactionChainHandler,
                executorService, domRpcServiceHandler.get());
        domRpcProviderService.registerRpcImplementation(establishSubscriptionRpc, RPC_ESTABLISH);

        final DOMRpcImplementation modifySubscriptionRpc = new ModifySubscriptionRpc(SUBSCRIPTIONS_HOLDER,
                domSchemaService, transactionChainHandler, executorService);
        domRpcProviderService.registerRpcImplementation(modifySubscriptionRpc, RPC_MODIFY);

        final DOMRpcImplementation deleteSubscriptionRpc = new RemoveSubscriptionRpc(SUBSCRIPTIONS_HOLDER,
                executorService, false);
        domRpcProviderService.registerRpcImplementation(deleteSubscriptionRpc, RPC_DELETE);

        final DOMRpcImplementation killSubscriptionRpc = new RemoveSubscriptionRpc(SUBSCRIPTIONS_HOLDER,
                executorService, true);
        domRpcProviderService.registerRpcImplementation(killSubscriptionRpc, RPC_KILL);
    }

    @Override
    public Set<Object> getSingletons() {
        return ImmutableSet.builder()
                .add(new SubscribedNotificationsImpl(SUBSCRIPTIONS_HOLDER))
                .build();
    }
}
