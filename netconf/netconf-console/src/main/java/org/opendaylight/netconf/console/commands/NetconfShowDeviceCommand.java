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

import javax.annotation.Nonnull;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.apache.karaf.shell.table.ShellTable;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

import com.google.common.base.Strings;

@Command(name = "netconf:show-device", scope = "netconf", description = "Shows netconf device attributes.")
public class NetconfShowDeviceCommand extends AbstractAction {

    protected final NetconfConsoleProvider service;

    public NetconfShowDeviceCommand(final NetconfConsoleProvider service) {
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
            required = false,
            multiValued = false)
    private String devicePort;

    @Override
    protected Object doExecute() throws Exception {
        if (!NetconfCommandUtils.isIpValid(deviceIp)
                || (devicePort != null && !NetconfCommandUtils.isPortValid(devicePort))) {
            return "Invalid IP or Port. Please enter a valid entry to proceed.";
        }

        final Map<String, Map<String, List<String>>> devices = service.showDevice(deviceIp, devicePort);
        printDeviceData(devices);
        return null;
    }

    private void printDeviceData(@Nonnull final Map<String, Map<String, List<String>>> devices) {
        final ShellTable table = new ShellTable();
        table.column(NetconfConsoleConstants.NETCONF_ID).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_IP).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_PORT).alignLeft();
        table.column(NetconfConsoleConstants.STATUS).alignLeft();
        table.column(NetconfConsoleConstants.AVAILABLE_CAPABILITIES).alignLeft();

        for (final String nodeId : devices.keySet()) {
            final Map<String, List<String>> device = devices.get(nodeId);
            table.addRow().addContent(nodeId,
                    device.get(NetconfConsoleConstants.NETCONF_IP).get(NetconfConsoleConstants.DEFAULT_INDEX),
                    device.get(NetconfConsoleConstants.NETCONF_PORT).get(NetconfConsoleConstants.DEFAULT_INDEX),
                    device.get(NetconfConsoleConstants.STATUS).get(NetconfConsoleConstants.DEFAULT_INDEX),
                    device.get(NetconfConsoleConstants.AVAILABLE_CAPABILITIES).get(NetconfConsoleConstants.DEFAULT_INDEX));
            formatCapabilities(device, table, NetconfConsoleConstants.AVAILABLE_CAPABILITIES);
        }
        table.print(System.out);
    }

    private void formatCapabilities(final Map<String, List<String>> device, final ShellTable table, final String capabilityName) {
        for (final String availableCapability : device.get(capabilityName)) {
            // Skip formatting the first capability for printing to console
            if (!Strings.isNullOrEmpty(availableCapability)
                    && !isFirstAvailableCapability(device, capabilityName, availableCapability)) {
                table.addRow().addContent("", "", "", "", availableCapability);
            }
        }
    }

    private boolean isFirstAvailableCapability(final Map<String, List<String>> device, final String capabilityName,
            final String availableCapability) {
        return device.get(capabilityName).indexOf(availableCapability) == NetconfConsoleConstants.DEFAULT_INDEX;
    }
}
