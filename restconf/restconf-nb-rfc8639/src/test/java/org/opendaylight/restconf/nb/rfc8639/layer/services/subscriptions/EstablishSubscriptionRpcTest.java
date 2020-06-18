/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class EstablishSubscriptionRpcTest {
    private static final DOMRpcIdentifier RPC_ESTABLISH = DOMRpcIdentifier.create(SchemaPath.create(
            true, QName.create(EstablishSubscriptionInput.QNAME.getModule(), "establish-subscription")));

    // the application uses only one replay buffer for all notifications
    private static final Map<String, ReplayBuffer> REPLAY_BUFFERS_FOR_NOTIFICATIONS = new ConcurrentHashMap<>();

    // the application uses only one subscription holder
    private static final SubscriptionsHolder SUBSCRIPTIONS_HOLDER = new SubscriptionsHolder(
            new SubscriptionIdGenerator.Random());

    private static final ListeningExecutorService EXECUTOR = MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(1));

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
    private DOMRpcService domRpcService;

    private static EffectiveModelContext effectiveModelContext;

    @BeforeClass
    public static void beforeClass() {
        effectiveModelContext = YangParserTestUtils.parseYangResources(
                EstablishSubscriptionRpcTest.class,
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
    }

    @Before
    public void setUp() {
        doReturn(effectiveModelContext).when(domSchemaService).getGlobalContext();

        final ListenableFuture<? extends DOMRpcResult> rpcResult = Futures.immediateFuture(mock(DOMRpcResult.class));
        doReturn(rpcResult).when(domRpcService).invokeRpc(any(), any());

        final DOMDataTreeReadWriteTransaction rwTx = mock(DOMDataTreeReadWriteTransaction.class);
        final FluentFuture<? extends CommitInfo> submit = FluentFuture.from(Futures.immediateFuture(null));
        doReturn(submit).when(rwTx).commit();
        doReturn(domTransactionChain).when(transactionChainHandler).get();
        doReturn(rwTx).when(domTransactionChain).newReadWriteTransaction();
        doReturn(mock(ListenerRegistration.class)).when(domNotificationService)
                .registerNotificationListener(any(), anyCollection());
    }

    @Test
    public void establishSubscriptionTest() throws Exception {
        establishSubscription(createSubscriptionInput("NETCONF"));
    }

    @Test
    public void establishSubscriptionMountPointTest() throws Exception {
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        doReturn(effectiveModelContext).when(mountPoint).getEffectiveModelContext();
        doReturn(Optional.of(domNotificationService)).when(mountPoint).getService(DOMNotificationService.class);
        doReturn(Optional.of(domRpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(mountPoint)).when(domMountPointService).getMountPoint(any());

        establishSubscription(createSubscriptionInput(
                "network-topology:network-topology/topology=topology-netconf/node=device1/yang-ext:mount/NETCONF"));
    }

    private NormalizedNode<?, ?> createSubscriptionInput(final String stream) {
        final LeafNode<Object> leafNode = Builders.leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID).withValue(stream).build();
        final AugmentationIdentifier augmentationNodeId = new AugmentationIdentifier(ImmutableSet.of(
                SubscribedNotificationsModuleUtils.STREAM_LEAF_ID.getNodeType(),
                SubscribedNotificationsModuleUtils.REPLAY_START_TIME_LEAF_ID.getNodeType()));
        final AugmentationNode augmentationNode = Builders.augmentationBuilder().withNodeIdentifier(augmentationNodeId)
                .addChild(leafNode).build();
        final ContainerNode containerNode = Builders.containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.TARGET_CHOICE_ID)
                .addChild(augmentationNode)
                .build();
        return Builders.containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.ESTABLISH_SUBSCRIPTION_INPUT)
                .withChild(containerNode)
                .build();
    }

    private void establishSubscription(final NormalizedNode<?, ?> input) throws Exception {
        final EstablishSubscriptionRpc establishSubscriptionRpc = new EstablishSubscriptionRpc(SUBSCRIPTIONS_HOLDER,
                REPLAY_BUFFERS_FOR_NOTIFICATIONS, domNotificationService, domSchemaService,
                domMountPointService, transactionChainHandler, EXECUTOR, domRpcService);

        final DOMRpcResult result = establishSubscriptionRpc.invokeRpc(RPC_ESTABLISH, input).get();
        Assert.assertTrue(result.getErrors().isEmpty());
    }
}
