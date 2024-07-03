/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;

@ExtendWith(MockitoExtension.class)
class ReadTransactionActorTest extends ReadTransactionActorTestAdapter {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataTreeReadTransaction mockReadTx;

    @BeforeEach
    void setUp() {
        init(mockReadTx, system, TestActorRef.create(system, ReadTransactionActor.props(mockReadTx)));
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }
}
