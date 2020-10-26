/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceSalFacadeTest {

    private NetconfDeviceSalFacade deviceFacade;

    @Mock
    private NetconfDeviceTopologyAdapter netconfDeviceTopologyAdapter;
    @Mock
    private NetconfDeviceSalProvider.MountInstance mountInstance;
    @Mock
    private NetconfDeviceSalProvider salProvider;
    @Mock
    private DataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", address);

        deviceFacade = new NetconfDeviceSalFacade(remoteDeviceId, salProvider, dataBroker, "mockTopo");

        doReturn(netconfDeviceTopologyAdapter).when(salProvider).getTopologyDatastoreAdapter();
        doNothing().when(netconfDeviceTopologyAdapter)
                .updateDeviceData(any(Boolean.class), any(NetconfDeviceCapabilities.class));

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
        final EffectiveModelContext schemaContext = mock(EffectiveModelContext.class);

        final NetconfSessionPreferences netconfSessionPreferences =
                NetconfSessionPreferences.fromStrings(getCapabilities());

        final DOMRpcService deviceRpc = mock(DOMRpcService.class);
        deviceFacade.onDeviceConnected(new EmptyMountPointContext(schemaContext), netconfSessionPreferences, deviceRpc,
            null);

        verify(mountInstance, times(1)).onTopologyDeviceConnected(eq(schemaContext),
                any(DOMDataBroker.class), any(NetconfDataTreeService.class), eq(deviceRpc),
                any(NetconfDeviceNotificationService.class), isNull());
        verify(netconfDeviceTopologyAdapter,
                times(1)).updateDeviceData(true, netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    @Test
    public void testOnDeviceNotification() throws Exception {
        final DOMNotification domNotification = mock(DOMNotification.class);
        deviceFacade.onNotification(domNotification);
        verify(mountInstance).publish(domNotification);
    }

    private static List<String> getCapabilities() {
        return Arrays.asList(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }
}
