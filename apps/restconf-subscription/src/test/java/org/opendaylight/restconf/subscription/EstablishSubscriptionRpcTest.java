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
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class EstablishSubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");
    private static final Uint32 ID = Uint32.valueOf(2147483648L);

    @Mock
    private DOMDataBroker dataBroker;
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
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Mock
    private RestconfStream<?> restconfStream;
    @Captor
    private ArgumentCaptor<RequestException> response;

    private EstablishSubscriptionRpc rpc;

    @BeforeEach
    void before() {
        rpc = new EstablishSubscriptionRpc(streamRegistry);
    }

    @Disabled
    @Test
    void establishSubscriptionTest() {
        final var nodeTarget = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
                .create(SubscriptionUtil.QNAME_TARGET))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF"))
            .build();
        final var nodeReceivers = ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                .create(QName.create(Subscription.QNAME, "receivers").intern()))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Receiver.QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
                        SubscriptionUtil.QNAME_RECEIVER_NAME, "unknown"))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_NAME, "unknown"))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_STATE,
                        Receiver.State.Active.getName()))
                    .build())
                .build())
            .build();
        final var expectedNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, ID))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ENCODING, EncodeJson$I.QNAME))
            .withChild(nodeReceivers)
            .withChild(nodeTarget)
            .build();

        final var responseBuilder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionOutput.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(EstablishSubscriptionOutput.QNAME, "id"), ID))
            .build();

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(restconfStream).when(streamRegistry).lookupStream("NETCONF");
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, getInput()));
        verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL),
            eq(SubscriptionUtil.SUBSCRIPTIONS.node(expectedNode.name())),
            eq(expectedNode));
        verify(request).completeWith(eq(responseBuilder));
    }

    @Disabled
    @Test
    void establishSubscriptionWrongStreamTest() {
        doReturn(null).when(streamRegistry).lookupStream("NETCONF");
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, getInput()));
        verify(request).completeWith(response.capture());
        assertEquals("NETCONF refers to an unknown stream", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWrongInputTest() {
        final var input = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .build();
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).completeWith(response.capture());
        assertEquals("No stream specified", response.getValue().getMessage());
    }

    @Disabled
    @Test
    void establishSubscriptionWrongFilterTest() {
        final var input = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.newChoiceBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Target.QNAME))
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF"))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(StreamFilter.QNAME))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER, "filter"))
                    .build())
                .build())
            .build();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(restconfStream).when(streamRegistry).lookupStream("NETCONF");
        doReturn(FluentFutures.immediateFalseFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(StreamFilter.QNAME,
                SubscriptionUtil.QNAME_STREAM_FILTER_NAME, "filter")));
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).completeWith(response.capture());

        assertEquals("filter refers to an unknown stream filter", response.getValue().getMessage());
    }

    private static ContainerNode getInput() {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ENCODING, EncodeJson$I.QNAME))
            .withChild(ImmutableNodes.newChoiceBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Target.QNAME))
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF"))
                .build())
            .build();
    }
}
