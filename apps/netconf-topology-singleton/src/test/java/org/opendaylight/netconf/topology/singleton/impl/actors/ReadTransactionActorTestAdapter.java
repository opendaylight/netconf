/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status.Failure;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * Adapter for read transaction tests.
 *
 * @author Thomas Pantelis
 */
abstract class ReadTransactionActorTestAdapter {
    static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    static final Timeout TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);
    static final ContainerNode NODE = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
            .build();

    private DOMDataTreeReadOperations mockReadTx;
    private TestProbe probe;
    private ActorRef actorRef;

    void init(final DOMDataTreeReadOperations inMockReadTx, final ActorSystem system,
            final ActorRef inActorRef) {
        mockReadTx = inMockReadTx;
        probe = TestProbe.apply(system);
        actorRef = inActorRef;
    }

    @Test
    void testRead() {
        doReturn(immediateFluentFuture(Optional.of(NODE))).when(mockReadTx).read(STORE, PATH);
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    void testReadEmpty() {
        doReturn(immediateFluentFuture(Optional.empty())).when(mockReadTx).read(STORE, PATH);
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    void testReadFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(mockReadTx).read(STORE, PATH);
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testExists() {
        doReturn(immediateTrueFluentFuture()).when(mockReadTx).exists(STORE, PATH);
        actorRef.tell(new ExistsRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).exists(STORE, PATH);
        probe.expectMsg(Boolean.TRUE);
    }

    @Test
    void testExistsFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        doReturn(immediateFailedFluentFuture(cause)).when(mockReadTx).exists(STORE, PATH);
        actorRef.tell(new ExistsRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).exists(STORE, PATH);
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }
}
