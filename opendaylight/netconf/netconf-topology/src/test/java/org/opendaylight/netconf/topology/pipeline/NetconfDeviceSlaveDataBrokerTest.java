/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import akka.actor.ActorSystem;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class NetconfDeviceSlaveDataBrokerTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID = new RemoteDeviceId("testing-device", new InetSocketAddress(9999));

    @Mock
    private ProxyNetconfDeviceDataBroker mockedDataBroker;

    @Mock
    private ActorSystem mockedActorSystem;

    private NetconfDeviceSlaveDataBroker slaveDataBroker;

    @Before
    public void setUp() {
        slaveDataBroker = new NetconfDeviceSlaveDataBroker(mockedActorSystem, REMOTE_DEVICE_ID, mockedDataBroker);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRegisterDataChangeListener() {
        slaveDataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY,
                mock(DOMDataChangeListener.class), AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateTransactionChain() {
        slaveDataBroker.createTransactionChain(mock(TransactionChainListener.class));
    }

    @Test
    public void testGetSupportedExtensions() {
        assertTrue(slaveDataBroker.getSupportedExtensions().isEmpty());
    }
}