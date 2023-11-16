/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@ExtendWith(MockitoExtension.class)
class NetconfCommandsImplCallsTest {
    @Mock
    private NetconfCommands netconfCommands;

    @Test
    void testConnectDeviceCommand() throws Exception {
        var netconfConnectDeviceCommand = new NetconfConnectDeviceCommand(netconfCommands);
        assertEquals("Invalid IP:null or Port:nullPlease enter a valid entry to proceed.",
            netconfConnectDeviceCommand.execute());
        verify(netconfCommands, times(0)).connectDevice(any(), any());

        netconfConnectDeviceCommand = new NetconfConnectDeviceCommand(netconfCommands, "192.168.1.1", "7777", "user",
            "pass");

        assertEquals("Netconf connector added succesfully", netconfConnectDeviceCommand.execute());
        verify(netconfCommands, times(1)).connectDevice(any(), any());
    }

    @Test
    void testDisconnectDeviceCommand() throws Exception {
        var netconfDisconnectDeviceCommand = new NetconfDisconnectDeviceCommand(netconfCommands);
        assertEquals("Invalid IP:null or Port:nullPlease enter a valid entry to proceed.",
            netconfDisconnectDeviceCommand.execute());

        verify(netconfCommands, times(0)).disconnectDevice(any(), any());

        netconfDisconnectDeviceCommand = new NetconfDisconnectDeviceCommand(netconfCommands, "deviceId", null, null);

        doReturn(true).when(netconfCommands).disconnectDevice(any());
        assertEquals("Netconf connector disconnected succesfully", netconfDisconnectDeviceCommand.execute());

        verify(netconfCommands, times(1)).disconnectDevice(any());

        netconfDisconnectDeviceCommand = new NetconfDisconnectDeviceCommand(netconfCommands, null, "192.168.1.1",
            "7777");
        doReturn(true).when(netconfCommands).disconnectDevice(any(), any());
        assertEquals("Netconf connector disconnected succesfully", netconfDisconnectDeviceCommand.execute());

        verify(netconfCommands, times(1)).disconnectDevice(any(), any());
    }

    @Test
    void testListDeviceCommand() throws Exception {
        final var netconfListDeviceCommand = new NetconfListDevicesCommand(netconfCommands);
        doReturn(getDeviceHashMap()).when(netconfCommands).listDevices();

        assertNull(netconfListDeviceCommand.execute());

        verify(netconfCommands, times(1)).listDevices();
    }

    @Test
    void testShowDeviceCommand() throws Exception {
        var netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands);
        assertEquals("You must provide either the device Ip and the device Port or the device Id",
            netconfShowDeviceCommand.execute());

        verify(netconfCommands, times(0)).showDevice(any());

        netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands, "deviceId", null, null);

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any());
        assertNull(netconfShowDeviceCommand.execute());

        verify(netconfCommands, times(1)).showDevice(any());

        netconfShowDeviceCommand = new NetconfShowDeviceCommand(netconfCommands, null, "192.168.1.1", "7777");

        doReturn(getDeviceHashMap()).when(netconfCommands).showDevice(any(), any());
        assertNull(netconfShowDeviceCommand.execute());

        verify(netconfCommands, times(1)).showDevice(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUpdateDeviceCommand() throws Exception {
        final var netconfUpdateDeviceCommand = new NetconfUpdateDeviceCommand(netconfCommands, "192.168.1.1");
        doReturn("").when(netconfCommands).updateDevice(isNull(), isNull(), isNull(), any());

        assertEquals("", netconfUpdateDeviceCommand.execute());

        final var hashMapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(netconfCommands, times(1)).updateDevice(isNull(), isNull(), isNull(),
                hashMapArgumentCaptor.capture());

        assertTrue(hashMapArgumentCaptor.getValue().containsKey(NetconfConsoleConstants.NETCONF_IP));
        assertEquals("192.168.1.1", hashMapArgumentCaptor.getValue().get(NetconfConsoleConstants.NETCONF_IP));
    }

    private static Map<String, Map<String, List<String>>> getDeviceHashMap() {
        return Map.of("device", Map.of(
            NetconfConsoleConstants.NETCONF_IP, List.of("192.168.1.1"),
            NetconfConsoleConstants.NETCONF_PORT, List.of("7777"),
            NetconfConsoleConstants.STATUS, List.of("connecting"),
            NetconfConsoleConstants.AVAILABLE_CAPABILITIES, List.of("cap1", "cap2", "cap3")));
    }
}
