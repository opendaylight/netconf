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
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import scala.concurrent.duration.Duration;

public class WriteTransactionActorTest extends WriteTransactionActorTestAdapter {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataWriteTransaction mockWriteTx;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        init(mockWriteTx, system, TestActorRef.create(system,
                WriteTransactionActor.props(mockWriteTx, Duration.apply(2, TimeUnit.SECONDS))));
    }

    @AfterClass
    public static void staticTearDown() {
        TestKit.shutdownActorSystem(system, Boolean.TRUE);
    }
}
