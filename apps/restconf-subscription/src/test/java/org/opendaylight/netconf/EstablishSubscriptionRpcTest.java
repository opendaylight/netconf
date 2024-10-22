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
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

import java.net.URI;
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
import org.opendaylight.restconf.server.api.DatabindPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
public class EstablishSubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");
    private static final Uint32 ID = Uint32.valueOf(2147483648L);

    @Mock
    private DOMDataBroker dataBroker;
//    @Mock
//    private SubscriptionStateService subscriptionStateService;
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
        final var rpc = new EstablishSubscriptionRpc(mdsalService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                    .create(QName.create(Subscription.QNAME, "target")))
                .withChild((ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF")))
                .build());

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        final var nodeTargetBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(QName.create(Subscription.QNAME, "target").intern()));
        final var nodeReceiversBuilder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                .create(QName.create(Subscription.QNAME, "receivers").intern()))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Receiver.QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
                        SubscriptionUtil.QNAME_RECEIVER_NAME, "unknown"))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_NAME, "unknown"))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_STATE, "active"))
                    .build())
                .build());
        nodeBuilder.withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, ID));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, ID));
        nodeBuilder.withChild(nodeReceiversBuilder.build());
        nodeTargetBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF"));
        nodeBuilder.withChild(nodeTargetBuilder.build());

        final var responseBuilder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionOutput.QNAME))
            .withChild(ImmutableNodes.leafNode(NodeIdentifier.create(QName.create(EstablishSubscriptionOutput.QNAME,
                "id").intern()), ID))
            .build();

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(FluentFutures.immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME,
            SubscriptionUtil.QNAME_STREAM_NAME, "NETCONF")));
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL),
            eq(SubscriptionUtil.SUBSCRIPTIONS.node(nodeBuilder.build().name())),
            eq(nodeBuilder.build()));
        verify(request).completeWith(eq(responseBuilder));
        //final var expected = nodeBuilder.build();
        //(SUBSCRIPTIONS.node(NodeIdentifierWithPredicates
        //            .of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, 2147483648L)))
        //assertEquals(node.getValue(), expected);
    }

    @Test
    void establishSubscriptionWrongStreamTest() {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new EstablishSubscriptionRpc(mdsalService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                    .create(QName.create(Subscription.QNAME, "target")))
                .withChild((ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF")))
                .build());

        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(FluentFutures.immediateFalseFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME,
                SubscriptionUtil.QNAME_STREAM_NAME, "NETCONF")));
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
//        verify(request).completeWith(eq(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
//            "%s refers to an unknown stream", "NETCONF")));
        verify(request).completeWith(response.capture());
        assertEquals("NETCONF refers to an unknown stream", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWrongInputTest() {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new EstablishSubscriptionRpc(mdsalService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME));

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(request).completeWith(response.capture());
        assertEquals("No stream specified", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWrongFilterTest() {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new EstablishSubscriptionRpc(mdsalService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                    .create(QName.create(Subscription.QNAME, "target")))
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF"))
                .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                        .create(QName.create(Subscription.QNAME, "stream-filter")))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,"filter"))
                .build())
                .build());

        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(FluentFutures.immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME,
                SubscriptionUtil.QNAME_STREAM_NAME, "NETCONF")));
        doReturn(FluentFutures.immediateFalseFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(StreamFilter.QNAME,
                SubscriptionUtil.QNAME_STREAM_FILTER_NAME, "filter")));
        doReturn(session).when(request).session();


        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(request).completeWith(response.capture());

        assertEquals("filter refers to an unknown stream filter", response.getValue().getMessage());
    }
}
