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
import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;

public class ReadTransactionActorTest extends ReadTransactionActorTestAdapter {
    private static ActorSystem system = ActorSystem.apply();

    @Mock
    private DOMDataReadOnlyTransaction mockReadTx;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        init(mockReadTx, system, TestActorRef.create(system, ReadTransactionActor.props(mockReadTx)));
    }

    @AfterClass
    public static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }
}
