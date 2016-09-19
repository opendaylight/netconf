/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

public class NetconfCommandsImplCallsTest {

    @Mock
    private NetconfCommands netconfCommands;

    @Before
    public void setUp(){
        initMocks(this);
    }

    @Test
    public void testConnectDeviceCommand() throws Exception {
        final NetconfConnectDeviceCommand netconfConnectDeviceCommand = new NetconfConnectDeviceCommand(netconfCommands);
        netconfConnectDeviceCommand.doExecute();
        verify(netconfCommands, times(0)).connectDevice(any(), any());

        final Field f = NetconfConnectDeviceCommand.class.getDeclaredField("deviceIp");
        f.setAccessible(true);
        f.set(netconfConnectDeviceCommand, "192.168.1.1");

        final Field f2 = NetconfConnectDeviceCommand.class.getDeclaredField("devicePort");
        f2.setAccessible(true);
        f2.set(netconfConnectDeviceCommand, "7777");

        netconfConnectDeviceCommand.doExecute();
        doNothing().when(netconfCommands).connectDevice(any(), any());
        verify(netconfCommands, times(1)).connectDevice(any(), any());

    }

    @Test
    public void testDisconnectDeviceCommand() throws Exception {
        final NetconfDisconnectDeviceCommand netconfDisconnectDeviceCommand = new NetconfDisconnectDeviceCommand(netconfCommands);
        netconfDisconnectDeviceCommand.doExecute();

        verify(netconfCommands, times(0)).connectDevice(any(), any());

        final Field f3 = NetconfDisconnectDeviceCommand.class.getDeclaredField("deviceId");
        f3.setAccessible(true);
        f3.set(netconfDisconnectDeviceCommand, "deviceID");

        doReturn(true).when(netconfCommands).disconnectDevice(any());
        netconfDisconnectDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).disconnectDevice(any());

        final Field f = NetconfDisconnectDeviceCommand.class.getDeclaredField("deviceIp");
        f.setAccessible(true);
        f.set(netconfDisconnectDeviceCommand, "192.168.1.1");

        final Field f2 = NetconfDisconnectDeviceCommand.class.getDeclaredField("devicePort");
        f2.setAccessible(true);
        f2.set(netconfDisconnectDeviceCommand, "7777");

        f3.set(netconfDisconnectDeviceCommand, null);

        doReturn(true).when(netconfCommands).disconnectDevice(any(), any());
        netconfDisconnectDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).disconnectDevice(any(), any());
    }

    @Test
    public void testListDeviceCommand() throws Exception {
        final NetconfListDevicesCommand netconfListDeviceCommand = new NetconfListDevicesCommand(netconfCommands);
        doReturn(getDeviceHashMap()).when(netconfCommands).listDevices();

        netconfListDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).listDevices();
    }

    @Test
    public void testShowDeviceCommand() throws Exception {
        final NetconfShowDeviceCommand netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands);
        netconfShowDeviceCommand.doExecute();

        verify(netconfCommands, times(0)).showDevice(any());

        final Field f3 = NetconfShowDeviceCommand.class.getDeclaredField("deviceId");
        f3.setAccessible(true);
        f3.set(netconfShowDeviceCommand, "deviceID");

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any());
        netconfShowDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).showDevice(any());

        final Field f = NetconfShowDeviceCommand.class.getDeclaredField("deviceIp");
        f.setAccessible(true);
        f.set(netconfShowDeviceCommand, "192.168.1.1");

        final Field f2 = NetconfShowDeviceCommand.class.getDeclaredField("devicePort");
        f2.setAccessible(true);
        f2.set(netconfShowDeviceCommand, "7777");

        f3.set(netconfShowDeviceCommand, null);

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any(), any());
        netconfShowDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).showDevice(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateDeviceCommand() throws Exception {
        final NetconfUpdateDeviceCommand netconfUpdateDeviceCommand = new NetconfUpdateDeviceCommand(netconfCommands);

        final ArgumentCaptor<HashMap> hashMapArgumentCaptor = ArgumentCaptor.forClass(HashMap.class);

        final Field f = NetconfUpdateDeviceCommand.class.getDeclaredField("newIp");
        f.setAccessible(true);
        f.set(netconfUpdateDeviceCommand, "192.168.1.1");

        doReturn("").when(netconfCommands).updateDevice(anyString(), anyString(), anyString(), any());

        netconfUpdateDeviceCommand.doExecute();

        verify(netconfCommands, times(1)).updateDevice(anyString(), anyString(), anyString(), hashMapArgumentCaptor.capture());

        assertTrue(hashMapArgumentCaptor.getValue().containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertEquals("192.168.1.1", hashMapArgumentCaptor.getValue().get(NetconfConsoleConstants.NETCONF_IP));
    }

    private HashMap getDeviceHashMap() {
        final HashMap<String, Map<String, List<String>>> devices = new HashMap<>();
        final HashMap<String, List<String>> deviceMap = new HashMap<>();
        deviceMap.put(NetconfConsoleConstants.NETCONF_IP, Lists.newArrayList("192.168.1.1"));
        deviceMap.put(NetconfConsoleConstants.NETCONF_PORT, Lists.newArrayList("7777"));
        deviceMap.put(NetconfConsoleConstants.STATUS, Lists.newArrayList("connecting"));
        deviceMap.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, Lists.newArrayList("cap1", "cap2", "cap3"));
        devices.put("device", deviceMap);
        return devices;
    }

}
