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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.ServletInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
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

public class EstablishSubscriptionRpcTest {

    private NotificationsHolder notificationsHolder;
    private ServletInfo servletInfo;
    private ListeningExecutorService executor;
    private Map<QName, ReplayBuffer> replayBuffersForNotifications;

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
        MockitoAnnotations.initMocks(this);
        this.notificationsHolder = new NotificationsHolder(new SubscriptionIdGenerator.Random());
        this.servletInfo = new ServletInfo();
        this.servletInfo.setSessionId("sessionId");
        this.executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
        this.replayBuffersForNotifications = new HashMap<>();
    }

    @Test
    public void getNotificationDefinitionForStreamNameTest() {
        when(this.domSchemaService.getGlobalContext()).thenReturn(this.mockEffectiveModelContext);
        resolveNotifiDefWithTests("test:test");
    }

    @Test
    public void getNotificationDefinitionForStreamNameWithMPTest() {
        final DOMMountPoint domMP = mock(DOMMountPoint.class);
        final Optional<DOMMountPoint> domMPOpt = Optional.of(domMP);
        when(this.domMountPointService.getMountPoint(any())).thenReturn(domMPOpt);
        when(domMP.getEffectiveModelContext()).thenReturn(this.mockEffectiveModelContext);
        final Optional<DOMNotificationService> domNotifiServiceMPOpt =
                Optional.of(this.domNotificationService);
        when(domMP.getService(DOMNotificationService.class)).thenReturn(domNotifiServiceMPOpt);
        final DOMRpcService domRpcService = mock(DOMRpcService.class);
        final DOMRpcResult domRpcResult = mock(DOMRpcResult.class);
        final Collection<? extends RpcError> errors = new ArrayList<>();
        doReturn(errors).when(domRpcResult).getErrors();
        final ListenableFuture<? extends DOMRpcResult> rpcResultFuture = Futures.immediateFuture(domRpcResult);
        doReturn(rpcResultFuture).when(domRpcService).invokeRpc(any(), any());
        final Optional<DOMRpcService> domRpcServiceOpt = Optional.of(
                domRpcService);
        when(domMP.getService(DOMRpcService.class)).thenReturn(domRpcServiceOpt);
        resolveNotifiDefWithTests(
                "network-topology:network-topology/topology=topology-netconf/node=device1/yang-ext:mount/test:test");
    }

    @Test
    public void processRpcTest() {
        final EstablishSubscriptionRpc establishSubscriptionRpc = new EstablishSubscriptionRpc(this.notificationsHolder,
                this.replayBuffersForNotifications, this.domNotificationService, this.domSchemaService,
                this.domMountPointService, this.transactionChainHandler, this.servletInfo, this.executor);
        final EffectiveModelContext schemaContext = YangParserTestUtils.parseYangResources(
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
        when(this.domSchemaService.getGlobalContext()).thenReturn(schemaContext);

        final DOMDataTreeReadWriteTransaction rwTx = mock(DOMDataTreeReadWriteTransaction.class);
        final FluentFuture<Boolean> existsFuture = FluentFuture.from(Futures.immediateFuture(true));
        when(rwTx.exists(any(), any())).thenReturn(existsFuture);
        doNothing().when(rwTx).put(any(), any(), any());
        final FluentFuture<? extends CommitInfo> submitFuture = FluentFuture.from(Futures.immediateFuture(null));
        doReturn(submitFuture).when(rwTx).commit();
        when(this.transactionChainHandler.get()).thenReturn(this.domTransactionChain);
        when(this.domTransactionChain.newReadWriteTransaction()).thenReturn(rwTx);

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
        when(notification.getQName()).thenReturn(notifiQName);
        notifications.add(notification);
        doReturn(notifications).when(module).getNotifications();
        modules.add(module);
        doReturn(modules).when(this.mockEffectiveModelContext).findModules("test");
        final EstablishSubscriptionRpc establishSubscriptionRpc = new EstablishSubscriptionRpc(this.notificationsHolder,
                this.replayBuffersForNotifications, this.domNotificationService, this.domSchemaService,
                this.domMountPointService, this.transactionChainHandler, this.servletInfo, this.executor);
        final Optional<StreamWrapper> wrapperOpt = establishSubscriptionRpc
                .getNotificationDefinitionForStreamName(stream);
        assertTrue(wrapperOpt.isPresent());
        final StreamWrapper wrapper = wrapperOpt.get();
        assertEquals(wrapper.getDomNotificationService(), this.domNotificationService);
        assertEquals(wrapper.getNotificationDefOpt().get().getQName(), notifiQName);
        assertEquals(wrapper.getNotificationDefOpt().get(), notification);
        assertEquals(wrapper.getSchemaContext(), this.mockEffectiveModelContext);
    }
}
