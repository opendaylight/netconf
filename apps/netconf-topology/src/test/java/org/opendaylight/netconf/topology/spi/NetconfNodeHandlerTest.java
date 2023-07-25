/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfNodeHandlerTest {

    @Mock
    private NetconfClientDispatcher clientDispatcher;
    @Mock
    private RemoteDeviceHandler delegate;

    private NetconfNodeHandler handler;

    @Before
    public void setUp() {
        handler = new NetconfNodeHandler(clientDispatcher, null, etc....);
    }

    /**
     * Test device reconnection logic with unlimited reconnections (set to 0).
     * </p>
     * Test that device is in {@link ConnectionStatus.Connecting} state when it's unable to connect,
     * and we have NOT yet reached the number of reconnection attempts.
     */
    @Test
    public void testDeviceConnecting() {
        verify(delegate, atLeastOnce()).onDeviceDisconnected();
        // IDK try to verify state "connecting"
    }

    /**
     * Test device reconnection logic with limited reconnection.
     * </p>
     * Test that device is in {@link ConnectionStatus.UnableToConnect} state when it's unable to connect,
     * and we have reached out the number of reconnection attempts.
     */
    @Test
    public void testDeviceUnableToConnect() {}

    /**
     * Test device successful reconnection.
     * </p>
     * Test that device IS connected successfully after series of unsuccessful tries by reconnection logic when number
     * of reconnection attempts is NOT exceeded.
     */
    @Test
    public void testDeviceConnected() {}

    /**
     * Test device unsuccessful reconnection.
     * </p>
     * Test that device is NOT connected after series of unsuccessful tries by reconnection logic when number
     * of reconnection attempts IS exceeded.
     */
    @Test
    public void testDeviceUnConnected() {}

}