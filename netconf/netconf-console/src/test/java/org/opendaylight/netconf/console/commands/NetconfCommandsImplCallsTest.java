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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfCommandsImplCallsTest {

    @Mock
    private NetconfCommands netconfCommands;

    @Test
    public void testConnectDeviceCommand() throws Exception {
        NetconfConnectDeviceCommand netconfConnectDeviceCommand =
                new NetconfConnectDeviceCommand(netconfCommands);
        netconfConnectDeviceCommand.execute();
        verify(netconfCommands, times(0)).connectDevice(any(), any());

        netconfConnectDeviceCommand = new NetconfConnectDeviceCommand(netconfCommands, "192.168.1.1", "7777", "user",
            "pass");

        netconfConnectDeviceCommand.execute();
        verify(netconfCommands, times(1)).connectDevice(any(), any());
    }

    @Test
    public void testDisconnectDeviceCommand() throws Exception {
        NetconfDisconnectDeviceCommand netconfDisconnectDeviceCommand =
                new NetconfDisconnectDeviceCommand(netconfCommands);
        netconfDisconnectDeviceCommand.execute();

        verify(netconfCommands, times(0)).disconnectDevice(any(), any());

        netconfDisconnectDeviceCommand = new NetconfDisconnectDeviceCommand(netconfCommands, "deviceId", null, null);

        doReturn(true).when(netconfCommands).disconnectDevice(any());
        netconfDisconnectDeviceCommand.execute();

        verify(netconfCommands, times(1)).disconnectDevice(any());

        netconfDisconnectDeviceCommand =
                new NetconfDisconnectDeviceCommand(netconfCommands, null, "192.168.1.1", "7777");

        doReturn(true).when(netconfCommands).disconnectDevice(any(), any());
        netconfDisconnectDeviceCommand.execute();

        verify(netconfCommands, times(1)).disconnectDevice(any(), any());
    }

    @Test
    public void testListDeviceCommand() throws Exception {
        final NetconfListDevicesCommand netconfListDeviceCommand = new NetconfListDevicesCommand(netconfCommands);
        doReturn(getDeviceHashMap()).when(netconfCommands).listDevices();

        netconfListDeviceCommand.execute();

        verify(netconfCommands, times(1)).listDevices();
    }

    @Test
    public void testShowDeviceCommand() throws Exception {
        NetconfShowDeviceCommand netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands);
        netconfShowDeviceCommand.execute();

        verify(netconfCommands, times(0)).showDevice(any());

        netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands, "deviceId", null, null);

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any());
        netconfShowDeviceCommand.execute();

        verify(netconfCommands, times(1)).showDevice(any());

        netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands, null, "192.168.1.1", "7777");

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any(), any());
        netconfShowDeviceCommand.execute();

        verify(netconfCommands, times(1)).showDevice(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateDeviceCommand() throws Exception {
        final NetconfUpdateDeviceCommand netconfUpdateDeviceCommand =
                new NetconfUpdateDeviceCommand(netconfCommands, "192.168.1.1");

        final ArgumentCaptor<HashMap> hashMapArgumentCaptor = ArgumentCaptor.forClass(HashMap.class);

        doReturn("").when(netconfCommands).updateDevice(isNull(), isNull(), isNull(), any(HashMap.class));

        netconfUpdateDeviceCommand.execute();

        verify(netconfCommands, times(1)).updateDevice(isNull(), isNull(), isNull(),
                hashMapArgumentCaptor.capture());

        assertTrue(hashMapArgumentCaptor.getValue().containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertEquals("192.168.1.1", hashMapArgumentCaptor.getValue().get(NetconfConsoleConstants.NETCONF_IP));
    }

    private static HashMap<String, Map<String, List<String>>> getDeviceHashMap() {
        final HashMap<String, Map<String, List<String>>> devices = new HashMap<>();
        final HashMap<String, List<String>> deviceMap = new HashMap<>();
        deviceMap.put(NetconfConsoleConstants.NETCONF_IP, Arrays.asList("192.168.1.1"));
        deviceMap.put(NetconfConsoleConstants.NETCONF_PORT, Arrays.asList("7777"));
        deviceMap.put(NetconfConsoleConstants.STATUS, Arrays.asList("connecting"));
        deviceMap.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, Arrays.asList("cap1", "cap2", "cap3"));
        devices.put("device", deviceMap);
        return devices;
    }

}
