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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.DatabindPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
public class DeleteSubscriptionRpcTest {
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
    private CompletingServerRequest<ContainerNode> request;
    @Mock
    private SubscriptionStateMachine stateMachine;
    @Mock
    private TransportSession session;
    @Captor
    private ArgumentCaptor<ServerException> response;

    private MdsalNotificationService mdsalService;
    private DeleteSubscriptionRpc rpc;

    private final YangInstanceIdentifier.NodeIdentifierWithPredicates identifier =
        YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, ID);
    final ContainerNode input = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(DeleteSubscriptionInput.QNAME))
        .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID))
        .build();

    @BeforeEach
    public void before() {
        mdsalService = new MdsalNotificationService(dataBroker);
        rpc = new DeleteSubscriptionRpc(mdsalService, subscriptionStateService, stateMachine);
        doReturn(SubscriptionState.ACTIVE).when(stateMachine).getSubscriptionState(ID);
    }

    @Test
    void deleteSubscriptionTest() {
        final var responseBuilder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(DeleteSubscriptionOutput.QNAME))
            .build();

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();
        doReturn(session).when(stateMachine).getSubscriptionSession(ID);

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(writeTx).delete(eq(LogicalDatastoreType.OPERATIONAL),
            eq(SubscriptionUtil.SUBSCRIPTIONS.node(identifier)));
        verify(request).completeWith(eq(responseBuilder));
    }

    @Test
    void deleteSubscriptionWrongIDTest() {
        doReturn(session).when(request).session();
        // Means there are no subscriptions, so no subscription with given id exist
        doReturn(null).when(stateMachine).getSubscriptionSession(ID);

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).completeWith(response.capture());
        assertEquals("Subscription with given id does not exist on this session",
            response.getValue().getMessage());
    }
}
