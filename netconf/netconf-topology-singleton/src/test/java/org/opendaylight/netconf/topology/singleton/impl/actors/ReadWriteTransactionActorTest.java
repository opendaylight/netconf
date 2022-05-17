/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import java.time.Duration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ReadWriteTransactionActorTest {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataTreeReadWriteTransaction mockReadWriteTx;

    private final ReadTransactionActorTestAdapter readTestAdapter = new ReadTransactionActorTestAdapter() {};
    private final WriteTransactionActorTestAdapter writeTestAdapter = new WriteTransactionActorTestAdapter() {};

    @Before
    public void setUp() {
        TestActorRef<?> actorRef = TestActorRef.create(system, ReadWriteTransactionActor.props(mockReadWriteTx,
                Duration.ofSeconds(2)));
        readTestAdapter.init(mockReadWriteTx, system, actorRef);
        writeTestAdapter.init(mockReadWriteTx, system, actorRef);
    }

    @AfterClass
    public static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    public void testRead() {
        readTestAdapter.testRead();
    }

    @Test
    public void testReadEmpty() {
        readTestAdapter.testReadEmpty();
    }

    @Test
    public void testReadFailure() {
        readTestAdapter.testReadFailure();
    }

    @Test
    public void testExists() {
        readTestAdapter.testExists();
    }

    @Test
    public void testExistsFailure() {
        readTestAdapter.testExistsFailure();
    }

    @Test
    public void testPut() {
        writeTestAdapter.testPut();
    }

    @Test
    public void testMerge() {
        writeTestAdapter.testMerge();
    }

    @Test
    public void testDelete() {
        writeTestAdapter.testDelete();
    }

    @Test
    public void testCancel() throws Exception {
        writeTestAdapter.testCancel();
    }

    @Test
    public void testSubmit() throws Exception {
        writeTestAdapter.testSubmit();
    }

    @Test
    public void testSubmitFail() throws Exception {
        writeTestAdapter.testSubmitFail();
    }

    @Test
    public void testIdleTimeout() throws Exception {
        writeTestAdapter.testIdleTimeout();
    }
}
