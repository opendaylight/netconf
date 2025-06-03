/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class EstablishSubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final QName STREAM_QNAME = QName.create(Subscription.QNAME, "stream");
    private static final NodeIdentifier STOP_TIME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stop-time").intern());


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
        final var idLeaf = QName.create(Subscription.QNAME, "id");
        final var nameLeaf = QName.create(Receiver.QNAME, "name");

        final var nodeTarget = ImmutableNodes.newChoiceBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(Subscription.QNAME, "target")))
            .withChild(ImmutableNodes.leafNode(STREAM_QNAME, "NETCONF"))
            .build();
        final var nodeReceivers = ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                .create(QName.create(Subscription.QNAME, "receivers").intern()))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Receiver.QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME, nameLeaf, "unknown"))
                    .withChild(ImmutableNodes.leafNode(nameLeaf, "unknown"))
                    .withChild(ImmutableNodes.leafNode(QName.create(Receiver.QNAME, "state"),
                        Receiver.State.Active.getName()))
                    .build())
                .build())
            .build();
        final var expectedNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME, idLeaf, ID))
            .withChild(ImmutableNodes.leafNode(idLeaf, ID))
            .withChild(ImmutableNodes.leafNode(QName.create(Subscription.QNAME, "encoding"), EncodeJson$I.QNAME))
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

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, getInput().build()));
        verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL), eq(YangInstanceIdentifier.of(
            new NodeIdentifier(Subscriptions.QNAME), new NodeIdentifier(Subscription.QNAME), expectedNode.name())),
            eq(expectedNode));
        verify(request).completeWith(eq(responseBuilder));
    }

    @Disabled
    @Test
    void establishSubscriptionWrongStreamTest() {
        doReturn(null).when(streamRegistry).lookupStream("NETCONF");
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, getInput().build()));
        verify(request).completeWith(response.capture());
        assertEquals("NETCONF refers to an unknown stream", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWrongInputTest() {
        final var input = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .build();

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
                .withChild(ImmutableNodes.leafNode(STREAM_QNAME, "NETCONF"))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(StreamFilter.QNAME))
                    .withChild(ImmutableNodes.leafNode(QName.create(Target.QNAME, "stream-filter-name"), "filter"))
                    .build())
                .build())
            .build();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(restconfStream).when(streamRegistry).lookupStream("NETCONF");
        doReturn(FluentFutures.immediateFalseFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            YangInstanceIdentifier.of(
                new NodeIdentifier(Filters.QNAME),
                new NodeIdentifier(StreamFilter.QNAME),
                NodeIdentifierWithPredicates.of(StreamFilter.QNAME,
                    QName.create(StreamFilter.QNAME, "name"), "filter")));
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).completeWith(response.capture());

        assertEquals("filter refers to an unknown stream filter", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWithStopTimeTest() {
        final var time = Instant.now().plus(Duration.ofDays(5));
        final var input = getInput()
            .withChild(ImmutableNodes.leafNode(STOP_TIME, time.toString()))
            .build();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(streamRegistry).establishSubscription(any(),  eq("NETCONF"),  eq(EncodeJson$I.QNAME), isNull(),
            eq(time));
    }

    @ParameterizedTest
    @MethodSource("invalidStopTimeProvider")
    void establishSubscriptionIncorrectStopTimeTest(String stopTime, String expectedMessage) {
        final var input = getInput()
            .withChild(ImmutableNodes.leafNode(STOP_TIME, stopTime))
            .build();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).completeWith(response.capture());
        assertEquals(expectedMessage, response.getValue().getMessage());
    }

    static Stream<Arguments> invalidStopTimeProvider() {
        return Stream.of(
            Arguments.of("1996-02-03T10:30:30+02:00", "Stop-time must be in future."), // correct time but in the past
            Arguments.of("2020-02-03T10:30:30+19:00",
                "Unable to parse time: java.time.format.DateTimeParseException:"
                + " Text '2020-02-03T10:30:30+19:00' could not be parsed at index 0"),  // offset out of range
            Arguments.of("2020-02-03T10:30:30+19:00[Europe/Paris]",
                "Unable to parse time: java.time.format.DateTimeParseException: Text '2020-02-03T10:30:30+19:00"
                + "[Europe/Paris]' could not be parsed at index 0"),  // time with specified time zone
            Arguments.of("just.some.text", "Unable to parse time: java.time.format.DateTimeParseException:"
                + " Text 'just.some.text' could not be parsed at index 0") // incorrect input
        );
    }

    private static @NonNull DataContainerNodeBuilder<NodeIdentifier, ContainerNode> getInput() {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(Subscription.QNAME, "encoding"), EncodeJson$I.QNAME))
            .withChild(ImmutableNodes.newChoiceBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Target.QNAME))
                .withChild(ImmutableNodes.leafNode(STREAM_QNAME, "NETCONF"))
                .build());
    }
}
