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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
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
    private DatabindPath.Rpc operationPath;
    @Mock
    private CompletingServerRequest<ContainerNode> request;
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Captor
    private ArgumentCaptor<RequestException> response;

    private EstablishSubscriptionRpc rpc;

    @BeforeEach
    void before() {
        rpc = new EstablishSubscriptionRpc(streamRegistry);
    }

    @Test
    void establishSubscriptionTest() {
        final var responseBuilder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionOutput.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(EstablishSubscriptionOutput.QNAME, "id"), ID))
            .build();

        doReturn(request).when(request).transform(any());

        doAnswer(inv -> {
            request.completeWith(responseBuilder);
            return null;
        }).when(streamRegistry).establishSubscription(
            any(), eq("NETCONF"), eq(EncodeJson$I.QNAME), isNull(), isNull());

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, getInput().build()));
        verify(streamRegistry).establishSubscription(any(), eq("NETCONF"), eq(EncodeJson$I.QNAME), isNull(), isNull());
        verify(request).completeWith(eq(responseBuilder));
    }

    @Test
    void establishSubscriptionWrongInputTest() {
        final var input = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .build();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(request).failWith(response.capture());
        assertEquals("No stream specified", response.getValue().getMessage());
    }

    @Test
    void establishSubscriptionWithStopTimeTest() {
        final var time = Instant.now().plus(Duration.ofDays(5));
        final var input = getInput()
            .withChild(ImmutableNodes.leafNode(STOP_TIME, time.toString()))
            .build();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, input));
        verify(streamRegistry).establishSubscription(any(), eq("NETCONF"), eq(EncodeJson$I.QNAME), isNull(),
            eq(time));
    }

    private static DataContainerNodeBuilder<NodeIdentifier, ContainerNode> getInput() {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(Subscription.QNAME, "encoding"), EncodeJson$I.QNAME))
            .withChild(ImmutableNodes.newChoiceBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Target.QNAME))
                .withChild(ImmutableNodes.leafNode(STREAM_QNAME, "NETCONF"))
                .build());
    }
}
