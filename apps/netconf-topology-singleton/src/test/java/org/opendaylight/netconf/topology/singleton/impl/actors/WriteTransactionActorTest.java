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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;

@ExtendWith(MockitoExtension.class)
class WriteTransactionActorTest extends WriteTransactionActorTestAdapter {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataTreeWriteTransaction mockWriteTx;

    @BeforeEach
    void setUp() {
        init(mockWriteTx, system, TestActorRef.create(system,
                WriteTransactionActor.props(mockWriteTx, Duration.ofSeconds(2))));
    }

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }
}
