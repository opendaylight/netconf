/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.commands;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@Service
@Command(name = "show-device", scope = "netconf", description = "Shows netconf device attributes.")
public class NetconfShowDeviceCommand implements Action {
    @Reference
    private NetconfCommands service;

    @Option(name = "-id",
            aliases = { "--identifier" },
            description = "Node Identifier of the netconf device",
            required = false,
            multiValued = false)
    String deviceId;

    @Option(name = "-i",
            aliases = { "--ipaddress" },
            description = "IP address of the netconf device",
            required = false,
            multiValued = false)
    String deviceIp;

    @Option(name = "-p",
            aliases = { "--port" },
            description = "Port of the netconf device",
            required = false,
            multiValued = false)
    String devicePort;

    public NetconfShowDeviceCommand() {
        // Nothing here, uses injection
    }

    @VisibleForTesting
    NetconfShowDeviceCommand(final NetconfCommands service) {
        this.service = requireNonNull(service);
    }

    @VisibleForTesting
    NetconfShowDeviceCommand(final NetconfCommands service, final String deviceId, final String deviceIp,
            final String devicePort) {
        this.service = requireNonNull(service);
        this.deviceId = deviceId;
        this.deviceIp = deviceIp;
        this.devicePort = devicePort;
    }

    @Override
    public String execute() {
        if ((Strings.isNullOrEmpty(deviceIp) || Strings.isNullOrEmpty(devicePort)) && Strings.isNullOrEmpty(deviceId)) {
            return "You must provide either the device Ip and the device Port or the device Id";
        }
        if (!Strings.isNullOrEmpty(deviceId)) {
            return printDeviceData(service.showDevice(deviceId));
        }
        if (!NetconfCommandUtils.isIpValid(deviceIp)
                || devicePort != null && !NetconfCommandUtils.isPortValid(devicePort)) {
            return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
        }
        return printDeviceData(service.showDevice(deviceIp, devicePort));
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private static String printDeviceData(final @NonNull Map<String, Map<String, List<String>>> devices) {
        final var table = new ShellTable();
        table.column(NetconfConsoleConstants.NETCONF_ID).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_IP).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_PORT).alignLeft();
        table.column(NetconfConsoleConstants.STATUS).alignLeft();
        table.column(NetconfConsoleConstants.AVAILABLE_CAPABILITIES).alignLeft();

        for (var entry : devices.entrySet()) {
            final var nodeId = entry.getKey();
            final var device = entry.getValue();
            table.addRow().addContent(nodeId,
                device.get(NetconfConsoleConstants.NETCONF_IP).get(NetconfConsoleConstants.DEFAULT_INDEX),
                device.get(NetconfConsoleConstants.NETCONF_PORT).get(NetconfConsoleConstants.DEFAULT_INDEX),
                device.get(NetconfConsoleConstants.STATUS).get(NetconfConsoleConstants.DEFAULT_INDEX),
                device.get(NetconfConsoleConstants.AVAILABLE_CAPABILITIES) .get(NetconfConsoleConstants.DEFAULT_INDEX));
            formatCapabilities(device, table, NetconfConsoleConstants.AVAILABLE_CAPABILITIES);
        }
        table.print(System.out);
        return null;
    }

    private static void formatCapabilities(final Map<String, List<String>> device, final ShellTable table,
            final String capabilityName) {
        for (var availableCapability : device.get(capabilityName)) {
            // First row is already added to table with the first available capability
            // Process rows other than the first to only have remaining available capabilities
            if (!Strings.isNullOrEmpty(availableCapability)
                    && !isFirstAvailableCapability(device, capabilityName, availableCapability)) {
                table.addRow().addContent("", "", "", "", availableCapability);
            }
        }
    }

    private static boolean isFirstAvailableCapability(final Map<String, List<String>> device,
            final String capabilityName, final String availableCapability) {
        return device.get(capabilityName).indexOf(availableCapability) == NetconfConsoleConstants.DEFAULT_INDEX;
    }
}
