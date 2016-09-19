/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.clustered;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class ClusteredNetconfDeviceCommunicatorTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID = new RemoteDeviceId("testing-device", new InetSocketAddress(9999));

    @Mock
    private ClusteredNetconfDevice remoteDevice;

    @Mock
    private EntityOwnershipService ownershipService;

    @Mock
    private NetconfClientSession session;

    @Mock
    private NetconfClientSessionListener listener1;

    @Mock
    private NetconfClientSessionListener listener2;

    @Mock
    private EntityOwnershipListenerRegistration ownershipListenerRegistration;

    private ClusteredNetconfDeviceCommunicator communicator;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(ownershipListenerRegistration).when(ownershipService).registerListener(
                "netconf-node/" + REMOTE_DEVICE_ID.getName(), remoteDevice);

        communicator = new ClusteredNetconfDeviceCommunicator(REMOTE_DEVICE_ID, remoteDevice, ownershipService, 10);
        communicator.registerNetconfClientSessionListener(listener1);
        communicator.registerNetconfClientSessionListener(listener2);
    }

    @Test
    public void testOnSessionDown() {
        communicator.onSessionUp(session);

        Exception exception = mock(Exception.class);
        communicator.onSessionDown(session, exception);

        verify(ownershipListenerRegistration).close();

        verify(listener1).onSessionDown(eq(session), eq(exception));
        verify(listener2).onSessionDown(eq(session), eq(exception));
    }

    @Test
    public void testOnSessionUp() {
        communicator.onSessionUp(session);

        verify(ownershipService).registerListener("netconf-node/" + REMOTE_DEVICE_ID.getName(), remoteDevice);

        verify(listener1).onSessionUp(eq(session));
        verify(listener2).onSessionUp(eq(session));
    }

    @Test
    public void testOnSessionTerminated() {
        communicator.onSessionUp(session);

        NetconfTerminationReason reason = mock(NetconfTerminationReason.class);
        communicator.onSessionTerminated(session, reason);

        verify(ownershipListenerRegistration).close();

        verify(listener1).onSessionTerminated(eq(session), eq(reason));
        verify(listener2).onSessionTerminated(eq(session), eq(reason));
    }
}