/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamSubscription;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class ModifySubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final ContainerNode INPUT = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
        .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID))
        .build();

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
    private TransportSession session;
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Mock
    private AbstractRestconfStreamSubscription subscription;
    @Captor
    private ArgumentCaptor<RequestException> responseCaptor;

    private ModifySubscriptionRpc rpc;

    @BeforeEach
    void before() {
        rpc = new ModifySubscriptionRpc(streamRegistry, subscriptionStateService);
    }

    @Disabled
    @Test
    void modifySubscriptionTest() {
        final var expectedNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME,
                SubscriptionUtil.QNAME_ID, ID))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID))
            .build();

        final var response = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
            .build();

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();
        doReturn(session).when(subscription).session();
        doReturn(subscription).when(streamRegistry).lookupSubscription(ID);
        doReturn(SubscriptionState.ACTIVE).when(subscription).state();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, INPUT));
        verify(writeTx).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(SubscriptionUtil.SUBSCRIPTIONS.node(expectedNode.name())),
            eq(expectedNode));
        verify(request).completeWith(eq(response));
    }

    @Test
    void modifySubscriptionWrongSessionTest() {
        doReturn(session).when(request).session();
        // return session different from request session
        doReturn(subscription).when(streamRegistry).lookupSubscription(ID);
        doReturn(SubscriptionState.ACTIVE).when(subscription).state();
        doReturn(null).when(subscription).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, INPUT));
        verify(request).completeWith(responseCaptor.capture());
        assertEquals("Subscription with given id does not exist on this session",
            responseCaptor.getValue().getMessage());
    }

    @Test
    void modifySubscriptionWrongIDTest() {
        doReturn(null).when(streamRegistry).lookupSubscription(ID);

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, INPUT));
        verify(request).completeWith(responseCaptor.capture());
        assertEquals("No subscription with given ID.", responseCaptor.getValue().getMessage());
    }

    @Test
    void modifySubscriptionAlreadyEndedTest() {
        doReturn(subscription).when(streamRegistry).lookupSubscription(ID);
        doReturn(SubscriptionState.END).when(subscription).state();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, INPUT));
        verify(request).completeWith(responseCaptor.capture());
        assertEquals("There is no active or suspended subscription with given ID.",
            responseCaptor.getValue().getMessage());
    }
}
