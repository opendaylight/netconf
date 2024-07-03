/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import java.time.Duration;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;

@ExtendWith(MockitoExtension.class)
class ReadWriteTransactionActorTest {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataTreeReadWriteTransaction mockReadWriteTx;

    private final ReadTransactionActorTestAdapter readTestAdapter = new ReadTransactionActorTestAdapter() {};
    private final WriteTransactionActorTestAdapter writeTestAdapter = new WriteTransactionActorTestAdapter() {};

    @BeforeEach
    void setUp() {
        TestActorRef<?> actorRef = TestActorRef.create(system, ReadWriteTransactionActor.props(mockReadWriteTx,
                Duration.ofSeconds(2)));
        readTestAdapter.init(mockReadWriteTx, system, actorRef);
        writeTestAdapter.init(mockReadWriteTx, system, actorRef);
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testRead() {
        readTestAdapter.testRead();
    }

    @Test
    void testReadEmpty() {
        readTestAdapter.testReadEmpty();
    }

    @Test
    void testReadFailure() {
        readTestAdapter.testReadFailure();
    }

    @Test
    void testExists() {
        readTestAdapter.testExists();
    }

    @Test
    void testExistsFailure() {
        readTestAdapter.testExistsFailure();
    }

    @Test
    void testPut() {
        writeTestAdapter.testPut();
    }

    @Test
    void testMerge() {
        writeTestAdapter.testMerge();
    }

    @Test
    void testDelete() {
        writeTestAdapter.testDelete();
    }

    @Test
    void testCancel() {
        writeTestAdapter.testCancel();
    }

    @Test
    void testSubmit() {
        writeTestAdapter.testSubmit();
    }

    @Test
    void testSubmitFail() {
        writeTestAdapter.testSubmitFail();
    }

    @Test
    void testIdleTimeout() {
        writeTestAdapter.testIdleTimeout();
    }
}
