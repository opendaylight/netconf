/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

/**
 * Adapter for read transaction tests.
 *
 * @author Thomas Pantelis
 */
public abstract class ReadTransactionActorTestAdapter {
    static final YangInstanceIdentifier PATH = YangInstanceIdentifier.EMPTY;
    static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    static final Timeout TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);
    static final NormalizedNode<?, ?> NODE = Builders.containerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "cont"))).build();

    private DOMDataReadTransaction mockReadTx;
    private TestProbe probe;
    private ActorRef actorRef;

    public void init(DOMDataReadTransaction inMockReadTx, ActorSystem system, ActorRef inActorRef) {
        this.mockReadTx = inMockReadTx;
        this.probe = TestProbe.apply(system);
        this.actorRef = inActorRef;
    }

    @Test
    public void testRead() {
        when(mockReadTx.read(STORE, PATH)).thenReturn(Futures.immediateCheckedFuture(Optional.of(NODE)));
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        final NormalizedNodeMessage response = probe.expectMsgClass(NormalizedNodeMessage.class);
        assertEquals(NODE, response.getNode());
    }

    @Test
    public void testReadEmpty() {
        when(mockReadTx.read(STORE, PATH)).thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    public void testReadFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        when(mockReadTx.read(STORE, PATH)).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        actorRef.tell(new ReadRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).read(STORE, PATH);
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    public void testExists() {
        when(mockReadTx.exists(STORE, PATH)).thenReturn(Futures.immediateCheckedFuture(true));
        actorRef.tell(new ExistsRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).exists(STORE, PATH);
        probe.expectMsg(true);
    }

    @Test
    public void testExistsFailure() {
        final ReadFailedException cause = new ReadFailedException("fail");
        when(mockReadTx.exists(STORE, PATH)).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        actorRef.tell(new ExistsRequest(STORE, PATH), probe.ref());

        verify(mockReadTx).exists(STORE, PATH);
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }
}
