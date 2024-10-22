/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.DatabindPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
public class ModifySubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");
    private static final Uint32 ID = Uint32.valueOf(2147483648L);

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private SubscriptionStateService subscriptionStateService;
    @Mock
    private DatabindPath.Rpc operationPath;
    @Mock
    private DOMDataTreeWriteTransaction writeTx;
    @Mock
    private DOMDataTreeReadTransaction readTx;
    @Mock
    private CompletingServerRequest<ContainerNode> request;
    @Mock
    private TransportSession session;
    @Captor
    private ArgumentCaptor<ServerException> response;

    @Test
    void establishSubscriptionTest() {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new ModifySubscriptionRpc(mdsalService, subscriptionStateService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(ModifySubscriptionInput.QNAME))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID));

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        nodeBuilder.withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, ID));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID));

        final var responseBuilder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
            .build();

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();

        final var subscription = new SubscriptionHolder(new SubscriptionBuilder().setId(new SubscriptionId(ID)).build(),
            mdsalService);
        doReturn(List.of(subscription)).when(session).getResources();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(writeTx).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(SubscriptionUtil.SUBSCRIPTIONS.node(nodeBuilder.build().name())),
            eq(nodeBuilder.build()));
        verify(request).completeWith(eq(responseBuilder));
    }

    @Test
    void establishSubscriptionWrongIDTest() {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new ModifySubscriptionRpc(mdsalService, subscriptionStateService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID));

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        nodeBuilder.withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, ID));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID));

        doReturn(session).when(request).session();
        doReturn(List.of()).when(session).getResources();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(request).completeWith(response.capture());
        assertEquals("Subscription with given id does not exist on this session",
            response.getValue().getMessage());
    }
}
