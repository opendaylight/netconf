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
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opendaylight.netconf.console.api.NetconfCommands;

@Service
@Command(name = "disconnect-device", scope = "netconf", description = "Disconnect netconf device.")
public class NetconfDisconnectDeviceCommand implements Action {
    @Reference
    private NetconfCommands service;

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

    @Option(name = "-id",
            aliases = { "--identifier" },
            description = "Node Identifier of the netconf device",
            required = false,
            multiValued = false)
    String deviceId;

    public NetconfDisconnectDeviceCommand() {
        // Nothing here, uses injection
    }

    @VisibleForTesting
    NetconfDisconnectDeviceCommand(final NetconfCommands service) {
        this.service = requireNonNull(service);
    }

    @VisibleForTesting
    NetconfDisconnectDeviceCommand(final NetconfCommands service, final String deviceId, final String deviceIp,
            final String devicePort) {
        this.service = requireNonNull(service);
        this.deviceId = deviceId;
        this.deviceIp = deviceIp;
        this.devicePort = devicePort;
    }

    @Override
    public String execute() {
        final boolean status;
        if (!Strings.isNullOrEmpty(deviceId)) {
            status = service.disconnectDevice(deviceId);
        } else {
            if (!NetconfCommandUtils.isIpValid(deviceIp) || !NetconfCommandUtils.isPortValid(devicePort)) {
                return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
            }
            status = service.disconnectDevice(deviceIp, devicePort);
        }
        return status ? "Netconf connector disconnected succesfully"
                : "Failed to disconnect netconf connector. Refer to karaf.log for details.";
    }
}
