/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import akka.actor.ActorSystem;
import akka.actor.Status.Success;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
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
public class ProxyDOMDataBrokerTest {
    private static final RemoteDeviceId DEVICE_ID =
            new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));

    private static ActorSystem system = ActorSystem.apply();

    private final TestProbe masterActor = new TestProbe(system);
    private final ProxyDOMDataBroker proxy = new ProxyDOMDataBroker(DEVICE_ID, masterActor.ref(), system.dispatcher(),
            Timeout.apply(5, TimeUnit.SECONDS));

    @AfterClass
    public static void staticTearDown() {
        TestKit.shutdownActorSystem(system, Boolean.TRUE);
    }

    @Test
    public void testNewReadOnlyTransaction() {
        final DOMDataReadOnlyTransaction tx = proxy.newReadOnlyTransaction();
        masterActor.expectMsgClass(NewReadTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY);
        masterActor.expectMsgClass(ReadRequest.class);
    }

    @Test
    public void testNewWriteOnlyTransaction() {
        final DOMDataWriteTransaction tx = proxy.newWriteOnlyTransaction();
        masterActor.expectMsgClass(NewWriteTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY);
        masterActor.expectMsgClass(DeleteRequest.class);
    }

    @Test
    public void testNewReadWriteTransaction() {
        final DOMDataReadWriteTransaction tx = proxy.newReadWriteTransaction();
        masterActor.expectMsgClass(NewReadWriteTransactionRequest.class);
        masterActor.reply(new Success(masterActor.ref()));

        assertEquals(DEVICE_ID, tx.getIdentifier());

        tx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY);
        masterActor.expectMsgClass(ReadRequest.class);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateTransactionChain() {
        proxy.createTransactionChain(null);
    }

    @Test
    public void testGetSupportedExtensions() {
        assertTrue(proxy.getSupportedExtensions().isEmpty());
    }
}
