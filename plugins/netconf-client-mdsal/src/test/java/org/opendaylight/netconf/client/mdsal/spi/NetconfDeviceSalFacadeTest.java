/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceSalFacadeTest {
    private final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", new InetSocketAddress("127.0.0.1", 8000));

    @Mock
    private NetconfDeviceMount mountInstance;

    private NetconfDeviceSalFacade deviceFacade;

    @Before
    public void setUp() throws Exception {
        doNothing().when(mountInstance).onDeviceDisconnected();

        deviceFacade = new NetconfDeviceSalFacade(remoteDeviceId, mountInstance, true);
    }

    @Test
    public void testOnDeviceDisconnected() {
        deviceFacade.onDeviceDisconnected();

        verify(mountInstance, times(1)).onDeviceDisconnected();
    }

    @Test
    public void testOnDeviceFailed() {
        final Throwable throwable = new Throwable();
        deviceFacade.onDeviceFailed(throwable);

        verify(mountInstance, times(1)).onDeviceDisconnected();
    }

    @Test
    public void testOnDeviceClose() throws Exception {
        deviceFacade.close();
        verify(mountInstance).close();
    }

    @Test
    public void testOnDeviceConnected() {
        final EffectiveModelContext schemaContext = mock(EffectiveModelContext.class);

        final var netconfSessionPreferences = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var deviceServices = new RemoteDeviceServices(mock(Rpcs.Normalized.class), null);
        deviceFacade.onDeviceConnected(
            new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(), MountPointContext.of(schemaContext)),
            netconfSessionPreferences, deviceServices);

        verify(mountInstance, times(1)).onDeviceConnected(eq(schemaContext), eq(deviceServices),
            any(DOMDataBroker.class), any(NetconfDataTreeService.class));
    }

    @Test
    public void testOnDeviceNotification() throws Exception {
        final DOMNotification domNotification = mock(DOMNotification.class);
        deviceFacade.onNotification(domNotification);
        verify(mountInstance).publish(domNotification);
    }
}
