/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.EstablishSubscriptionRpc.StreamWrapper;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class EstablishSubscriptionRpcTest {
    // the application uses only one replay buffer for all notifications
    private static final Map<QName, ReplayBuffer> REPLAY_BUFFERS_FOR_NOTIFICATIONS = new ConcurrentHashMap<>();

    private SubscriptionsHolder subscriptionsHolder;
    private ListeningExecutorService executor;

    @Mock
    private DOMTransactionChain domTransactionChain;

    @Mock
    private TransactionChainHandler transactionChainHandler;

    @Mock
    private DOMMountPointService domMountPointService;

    @Mock
    private DOMSchemaService domSchemaService;

    @Mock
    private DOMNotificationService domNotificationService;

    @Mock
    private EffectiveModelContext mockEffectiveModelContext;

    @Before
    public void setup() {
        subscriptionsHolder = new SubscriptionsHolder(new SubscriptionIdGenerator.Random());
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    }

    @Test
    public void getNotificationDefinitionForStreamNameTest() {
        doReturn(mockEffectiveModelContext).when(domSchemaService).getGlobalContext();
        resolveNotifiDefWithTests("test:test");
    }

    @Test
    public void getNotificationDefinitionForStreamNameWithMPTest() {
        final DOMMountPoint domMP = mock(DOMMountPoint.class);
        final Optional<DOMMountPoint> domMPOpt = Optional.of(domMP);
        doReturn(domMPOpt).when(domMountPointService).getMountPoint(any());
        doReturn(mockEffectiveModelContext).when(domMP).getEffectiveModelContext();
        final Optional<DOMNotificationService> domNotifiServiceMPOpt = Optional.of(domNotificationService);
        doReturn(domNotifiServiceMPOpt).when(domMP).getService(DOMNotificationService.class);
        final DOMRpcService domRpcService = mock(DOMRpcService.class);
        final DOMRpcResult domRpcResult = mock(DOMRpcResult.class);
        final ListenableFuture<? extends DOMRpcResult> rpcResultFuture = Futures.immediateFuture(domRpcResult);
        doReturn(rpcResultFuture).when(domRpcService).invokeRpc(any(), any());
        final Optional<DOMRpcService> domRpcServiceOpt = Optional.of(domRpcService);
        doReturn(domRpcServiceOpt).when(domMP).getService(DOMRpcService.class);

        resolveNotifiDefWithTests(
                "network-topology:network-topology/topology=topology-netconf/node=device1/yang-ext:mount/test:test");
    }

    @Test
    public void processRpcTest() {
        final EstablishSubscriptionRpc establishSubscriptionRpc = new EstablishSubscriptionRpc(subscriptionsHolder,
                REPLAY_BUFFERS_FOR_NOTIFICATIONS, domNotificationService, domSchemaService,
                domMountPointService, transactionChainHandler, executor);
        final EffectiveModelContext effectiveModelContext = YangParserTestUtils.parseYangResources(
                getClass(),
                "/ietf-interfaces@2018-02-20.yang",
                "/ietf-inet-types@2013-07-15.yang",
                "/ietf-ip@2018-02-22.yang",
                "/ietf-netconf-acm@2018-02-14.yang",
                "/ietf-network-instance@2019-01-21.yang",
                "/ietf-restconf@2017-01-26.yang",
                "/ietf-subscribed-notifications@2019-09-09.yang",
                "/ietf-yang-schema-mount@2019-01-14.yang",
                "/ietf-yang-types@2013-07-15.yang",
                "/test@2018-04-04.yang"
        );
        doReturn(effectiveModelContext).when(domSchemaService).getGlobalContext();

        final DOMDataTreeReadWriteTransaction rwTx = mock(DOMDataTreeReadWriteTransaction.class);
        final FluentFuture<? extends CommitInfo> submitFuture = FluentFuture.from(Futures.immediateFuture(null));
        doReturn(submitFuture).when(rwTx).commit();
        doReturn(domTransactionChain).when(transactionChainHandler).get();
        doReturn(rwTx).when(domTransactionChain).newReadWriteTransaction();
        doReturn(mock(ListenerRegistration.class)).when(domNotificationService)
                .registerNotificationListener(any(), anySet());

        final LeafNode<Object> leafNode = Builders.leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID).withValue("test:test").build();
        final AugmentationIdentifier augmentationNodeId = new AugmentationIdentifier(ImmutableSet.of(
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID.getNodeType(),
                SubscribedNotificationsModuleUtils.REPLAY_START_TIME_LEAF_ID.getNodeType()));
        final AugmentationNode augmentationNode = Builders.augmentationBuilder().withNodeIdentifier(augmentationNodeId)
                .addChild(leafNode).build();
        final ContainerNode containerNode = Builders.containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID)
                .addChild(augmentationNode)
                .build();
        final ContainerNode input = Builders.containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.ESTABLISH_SUBSCRIPTION_INPUT)
                .withChild(containerNode)
                .build();
        final DOMRpcResult rpcResult = establishSubscriptionRpc.processRpc(input);
        final NormalizedNode<?, ?> resultNN = rpcResult.getResult();
        assertNotNull(resultNN);
    }

    private void resolveNotifiDefWithTests(final String stream) {
        final Set<Module> modules = new HashSet<>();
        final Module module = mock(Module.class);
        final Collection<NotificationDefinition> notifications = new HashSet<>();
        final NotificationDefinition notification = mock(NotificationDefinition.class);
        final QName notifiQName = QName.create("t:s:t", "2018-04-04", "test");
        doReturn(notifiQName).when(notification).getQName();
        notifications.add(notification);
        doReturn(notifications).when(module).getNotifications();
        modules.add(module);
        doReturn(modules).when(mockEffectiveModelContext).findModules("test");
        final EstablishSubscriptionRpc establishSubscriptionRpc = new EstablishSubscriptionRpc(subscriptionsHolder,
                REPLAY_BUFFERS_FOR_NOTIFICATIONS, domNotificationService, domSchemaService,
                domMountPointService, transactionChainHandler, executor);
        final Optional<StreamWrapper> wrapperOpt = establishSubscriptionRpc
                .getNotificationDefinitionForStreamName(stream);
        assertTrue(wrapperOpt.isPresent());
        final StreamWrapper wrapper = wrapperOpt.get();
        assertEquals(domNotificationService, wrapper.getDomNotificationService());
        assertEquals(notifiQName, wrapper.getNotificationDefOpt().get().getQName());
        assertEquals(notification, wrapper.getNotificationDefOpt().get());
        assertEquals(mockEffectiveModelContext, wrapper.getEffectiveModelContext());
    }
}
