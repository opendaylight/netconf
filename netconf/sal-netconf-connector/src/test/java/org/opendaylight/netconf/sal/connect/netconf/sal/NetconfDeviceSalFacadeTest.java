/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({NetconfDeviceTopologyAdapter.class, NetconfDeviceSalProvider.MountInstance.class, NetconfSessionPreferences.class})
public class NetconfDeviceSalFacadeTest {

    private NetconfDeviceSalFacade deviceFacade;

    private NetconfDeviceTopologyAdapter netconfDeviceTopologyAdapter;
    private NetconfDeviceSalProvider.MountInstance mountInstance;

    @Mock
    private NetconfDeviceSalProvider salProvider;

    @Before
    public void setUp() throws Exception{
        initMocks(this);
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", address);

        final Broker domRegistry = mock(Broker.class);
        final BindingAwareBroker bindingRegistry = mock(BindingAwareBroker.class);
        deviceFacade = new NetconfDeviceSalFacade(remoteDeviceId, domRegistry, bindingRegistry);

        final Field f = NetconfDeviceSalFacade.class.getDeclaredField("salProvider");
        f.setAccessible(true);
        f.set(deviceFacade, salProvider);

        netconfDeviceTopologyAdapter = PowerMockito.mock(NetconfDeviceTopologyAdapter.class);
        mountInstance = PowerMockito.mock(NetconfDeviceSalProvider.MountInstance.class);

        doReturn(netconfDeviceTopologyAdapter).when(salProvider).getTopologyDatastoreAdapter();
        doNothing().when(netconfDeviceTopologyAdapter).updateDeviceData(any(Boolean.class), any(NetconfDeviceCapabilities.class));

        doReturn(mountInstance).when(salProvider).getMountInstance();
        doNothing().when(mountInstance).onTopologyDeviceDisconnected();
    }

    @Test
    public void testOnDeviceDisconnected() {
        deviceFacade.onDeviceDisconnected();

        verify(netconfDeviceTopologyAdapter).updateDeviceData(eq(false), any(NetconfDeviceCapabilities.class));
        verify(mountInstance, times(1)).onTopologyDeviceDisconnected();

    }

    @Test
    public void testOnDeviceFailed() {
        final Throwable throwable = new Throwable();
        deviceFacade.onDeviceFailed(throwable);

        verify(netconfDeviceTopologyAdapter).setDeviceAsFailed(throwable);
        verify(mountInstance, times(1)).onTopologyDeviceDisconnected();
    }

    @Test
    public void testOnDeviceClose() throws Exception {
        deviceFacade.close();
        verify(salProvider).close();
    }

    @Test
    public void testOnDeviceConnected() {
        final SchemaContext schemaContext = mock(SchemaContext.class);

        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(getCapabilities());

        final DOMRpcService deviceRpc = mock(DOMRpcService.class);
        deviceFacade.onDeviceConnected(schemaContext, netconfSessionPreferences, deviceRpc);

        verify(mountInstance, times(1)).onTopologyDeviceConnected(eq(schemaContext), any(DOMDataBroker.class), eq(deviceRpc), any(NetconfDeviceNotificationService.class));
        verify(netconfDeviceTopologyAdapter, times(1)).updateDeviceData(true, netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    @Test
    public void testOnDeviceNotification() throws Exception {
        final DOMNotification domNotification = mock(DOMNotification.class);
        deviceFacade.onNotification(domNotification);
        verify(mountInstance).publish(domNotification);
    }

   private List<String> getCapabilities(){
        return Arrays.asList(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }
}
