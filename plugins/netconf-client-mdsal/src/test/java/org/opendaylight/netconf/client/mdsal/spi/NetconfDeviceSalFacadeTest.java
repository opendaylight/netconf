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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceSalFacadeTest {
    private final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", new InetSocketAddress("127.0.0.1", 8000));

    @Mock
    private NetconfDeviceMount mountInstance;
    @Mock
    private EffectiveModelContext modelContext;
    @Mock
    private Rpcs.Normalized normalizedRpcs;
    @Mock
    private DOMNotification domNotification;

    private NetconfDeviceSalFacade deviceFacade;

    @BeforeEach
    void setUp() throws Exception {
        deviceFacade = new NetconfDeviceSalFacade(remoteDeviceId, mountInstance, true);
    }

    @Test
    void testOnDeviceDisconnected() {
        deviceFacade.onDeviceDisconnected();

        verify(mountInstance, times(1)).onDeviceDisconnected();
    }

    @Test
    void testOnDeviceFailed() {
        final Throwable throwable = new Throwable();
        deviceFacade.onDeviceFailed(throwable);

        verify(mountInstance, times(1)).onDeviceDisconnected();
    }

    @Test
    void testOnDeviceClose() {
        deviceFacade.close();
        verify(mountInstance).close();
    }

    @Test
    void testAfterDeviceClose() {
        deviceFacade.close();
        final var netconfSessionPreferences = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var deviceServices = new RemoteDeviceServices(normalizedRpcs, null);

        // Verify that onDeviceConnected is not called after close.
        deviceFacade.onDeviceConnected(
            new NetconfDeviceSchema(DatabindContext.ofModel(modelContext), NetconfDeviceCapabilities.empty()),
            netconfSessionPreferences, deviceServices);
        verify(mountInstance, times(0)).onDeviceConnected(eq(modelContext), any(), eq(deviceServices),
            any(DOMDataBroker.class), any(NetconfDataTreeService.class));

        // Verify that onDeviceDisconnected is not called after close.
        deviceFacade.onDeviceDisconnected();
        verify(mountInstance, times(0)).onDeviceDisconnected();

        // Verify that onDeviceDisconnected is not called after close.
        deviceFacade.onDeviceFailed(new Throwable());
        verify(mountInstance, times(0)).onDeviceDisconnected();
    }

    @Test
    void testOnDeviceConnected() {
        final var netconfSessionPreferences = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var deviceServices = new RemoteDeviceServices(normalizedRpcs, null);
        deviceFacade.onDeviceConnected(
            new NetconfDeviceSchema(DatabindContext.ofModel(modelContext), NetconfDeviceCapabilities.empty()),
            netconfSessionPreferences, deviceServices);

        verify(mountInstance, times(1)).onDeviceConnected(eq(modelContext), any(), eq(deviceServices), any(), any());
    }

    @Test
    void testOnDeviceNotification() {
        deviceFacade.onNotification(domNotification);
        verify(mountInstance).publish(domNotification);
    }
}
