/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status.Success;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Unit tests for ProxyDOMDataBroker.
 *
 * @author Thomas Pantelis
 */
class ProxyDOMDataBrokerTest {
    private static final RemoteDeviceId DEVICE_ID =
            new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));

    private static ActorSystem system = ActorSystem.apply();

    private final TestProbe masterActor = new TestProbe(system);
    private final ProxyDOMDataBroker proxy = new ProxyDOMDataBroker(DEVICE_ID, masterActor.ref(), system.dispatcher(),
            Timeout.apply(5, TimeUnit.SECONDS));

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testNewReadOnlyTransaction() {
        final DOMDataTreeReadTransaction tx = proxy.newReadOnlyTransaction();
        masterActor.expectMsgClass(NewReadTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        masterActor.expectMsgClass(ReadRequest.class);
    }

    @Test
    void testNewWriteOnlyTransaction() {
        final DOMDataTreeWriteTransaction tx = proxy.newWriteOnlyTransaction();
        masterActor.expectMsgClass(NewWriteTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        masterActor.expectMsgClass(DeleteRequest.class);
    }

    @Test
    void testNewReadWriteTransaction() {
        final DOMDataTreeReadWriteTransaction tx = proxy.newReadWriteTransaction();
        masterActor.expectMsgClass(NewReadWriteTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        masterActor.expectMsgClass(ReadRequest.class);
    }

    @Test
    void testCreateTransactionChain() {
        assertThrows(UnsupportedOperationException.class, proxy::createTransactionChain);
    }

    @Test
    void testGetSupportedExtensions() {
        assertEquals(List.of(), proxy.supportedExtensions());
    }
}
