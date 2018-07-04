/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.opendaylight.netconf.console.api.NetconfCommands;

@Command(name = "netconf:disconnect-device", scope = "netconf", description = "Disconnect netconf device.")
public class NetconfDisconnectDeviceCommand implements Action {

    protected final NetconfCommands service;

    public NetconfDisconnectDeviceCommand(final NetconfCommands service) {
        this.service = service;
    }

    @VisibleForTesting
    NetconfDisconnectDeviceCommand(final NetconfCommands service, final String deviceId, final String deviceIp,
                                   final String devicePort) {
        this.service = service;
        this.deviceId = deviceId;
        this.deviceIp = deviceIp;
        this.devicePort = devicePort;
    }

    @Option(name = "-i",
            aliases = { "--ipaddress" },
            description = "IP address of the netconf device",
            required = false,
            multiValued = false)
    private String deviceIp;

    @Option(name = "-p",
            aliases = { "--port" },
            description = "Port of the netconf device",
            required = false,
            multiValued = false)
    private String devicePort;

    @Option(name = "-id",
            aliases = { "--identifier" },
            description = "Node Identifier of the netconf device",
            required = false,
            multiValued = false)
    private String deviceId;

    @Override
    public Object execute() {
        boolean status = false;
        if (!Strings.isNullOrEmpty(deviceId)) {
            status = service.disconnectDevice(deviceId);
        } else {
            if (!NetconfCommandUtils.isIpValid(deviceIp) || !NetconfCommandUtils.isPortValid(devicePort)) {
                return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
            }
            status = service.disconnectDevice(deviceIp, devicePort);
        }
        final String message = status ? "Netconf connector disconnected succesfully"
                : "Failed to disconnect netconf connector. Refer to karaf.log for details.";
        return message;
    }
}
