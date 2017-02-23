/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx.master;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.TransactionInUseException;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

public class MasterDOMWriteTransactionTest {

    private static final FiniteDuration TIMEOUT = FiniteDuration.apply(5, TimeUnit.SECONDS);
    @Mock
    private DOMDataBroker broker;
    @Mock
    private DOMDataWriteTransaction delegate;
    private ActorRef user1;
    private ActorRef user2;
    private ActorSystem system;
    private MasterDOMWriteTransaction tx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final RemoteDeviceId id = new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
        when(broker.newWriteOnlyTransaction()).thenReturn(delegate);
        when(delegate.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        when(delegate.cancel()).thenReturn(true);
        tx = new MasterDOMWriteTransaction(id, broker);
        system = ActorSystem.apply();
        user1 = TestProbe.apply("user1", system).ref();
        user2 = TestProbe.apply("user2", system).ref();
    }

    @Test
    public void testOpen() throws Exception {
        tx.openTransaction(user1);
        verify(broker).newWriteOnlyTransaction();
    }

    @Test
    public void testMultipleOpensByDifferentUsers() throws Exception {
        tx.openTransaction(user1);
        try {
            tx.openTransaction(user2);
            Assert.fail("TransactionInUseException should be thrown");
        } catch (final TransactionInUseException e) {
            verify(broker, times(1)).newWriteOnlyTransaction();
        }
    }

    @Test
    public void testMultipleOpensBySameUser() throws Exception {
        tx.openTransaction(user1);
        try {
            tx.openTransaction(user1);
            Assert.fail("TransactionInUseException should be thrown");
        } catch (final TransactionInUseException e) {
            verify(broker, times(1)).newWriteOnlyTransaction();
        }
    }

    @Test
    public void testSubmit() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        Await.result(tx.submit(user1), TIMEOUT);
        verify(delegate).submit();
    }

    @Test
    public void testSubmitWithoutOpen() throws Exception {
        verify(broker, never()).newWriteOnlyTransaction();
        try {
            Await.result(tx.submit(user1), TIMEOUT);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, never()).submit();
        }
    }

    @Test
    public void testSubmitByAnotherUser() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        try {
            Await.result(tx.submit(user2), TIMEOUT);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, never()).submit();
        }
    }

    @Test
    public void testDoubleSubmit() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        try {
            Await.result(tx.submit(user1), TIMEOUT);
            Await.result(tx.submit(user1), TIMEOUT);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, times(1)).submit();
        }
    }

    @Test
    public void testCancel() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        Assert.assertTrue(tx.cancel(user1));
        verify(delegate).cancel();
    }

    @Test
    public void testCancelWithoutOpen() throws Exception {
        verify(broker, never()).newWriteOnlyTransaction();
        try {
            tx.cancel(user1);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, never()).cancel();
        }
    }

    @Test
    public void testCancelByAnotherUser() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        try {
            tx.cancel(user2);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, never()).cancel();
        }
    }

    @Test
    public void testDoubleCancel() throws Exception {
        tx.openTransaction(user1);
        verify(broker, times(1)).newWriteOnlyTransaction();
        try {
            tx.cancel(user1);
            tx.cancel(user1);
            Assert.fail("IllegalStateException should be thrown");
        } catch (final IllegalStateException e) {
            verify(delegate, times(1)).cancel();
        }
    }


    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(system);
    }

}