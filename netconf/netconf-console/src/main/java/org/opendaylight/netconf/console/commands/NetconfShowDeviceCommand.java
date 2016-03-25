/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.List;
import java.util.Map;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.apache.karaf.shell.table.ShellTable;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:show-device", scope = "netconf", description = "Shows netconf device attributes.")
public class NetconfShowDeviceCommand extends AbstractAction {

    private static final int DEFAULT_INDEX = 0;
    private static final String AVAILABLE_CAPABILITIES = "available capabilities";
    private static final String STATUS = "status";
    private static final String NETCONF_PORT = "netconf Port";
    private static final String NETCONF_IP = "netconf Ip";
    private static final String NETCONF_ID = "netconf Id";

    protected NetconfConsoleProvider service;

    public NetconfShowDeviceCommand(NetconfConsoleProvider service) {
        this.service = service;
    }

    @Option(name = "-i",
            aliases = { "--ipaddress" },
            description = "IP address of the netconf device",
            required = true,
            multiValued = false)
    private String deviceIp = "";

    @Option(name = "-p",
            aliases = { "--port" },
            description = "Port of the netconf device",
            required = true,
            multiValued = false)
    private String devicePort;

    @Override
    protected Object doExecute() throws Exception {
        Map<String, Map<String, List<String>>> devices = service.showDevice(deviceIp, devicePort);
        printDeviceData(devices);
        return null;
    }

    private void printDeviceData(Map<String, Map<String, List<String>>> devices) {
        ShellTable table = new ShellTable();
        table.column(NETCONF_ID).alignLeft();
        table.column(NETCONF_IP).alignLeft();
        table.column(NETCONF_PORT).alignLeft();
        table.column(STATUS).alignLeft();
        table.column(AVAILABLE_CAPABILITIES).alignLeft();

        for (String nodeId : devices.keySet()) {
            Map<String, List<String>> device = devices.get(nodeId);
            table.addRow().addContent(nodeId,
                    device.get(NETCONF_IP).get(DEFAULT_INDEX),
                    device.get(NETCONF_PORT).get(DEFAULT_INDEX),
                    device.get(STATUS).get(DEFAULT_INDEX), device.get(AVAILABLE_CAPABILITIES).get(DEFAULT_INDEX));//formatCapabilities(device, table, AVAILABLE_CAPABILITIES));
            formatCapabilities(device, table, AVAILABLE_CAPABILITIES);
        }
        table.print(System.out);
    }

    private void formatCapabilities(Map<String, List<String>> device, ShellTable table, String capabilityName) {
        for (String availableCapability : device.get(capabilityName)) {
            if (availableCapability != null && availableCapability != ""
                    && device.get(capabilityName).indexOf(availableCapability) != DEFAULT_INDEX) {
                table.addRow().addContent("", "", "", "", availableCapability);
            }
        }
    }
}
