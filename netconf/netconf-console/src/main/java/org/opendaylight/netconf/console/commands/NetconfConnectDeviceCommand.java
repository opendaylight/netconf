/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:connect-device", scope = "netconf", description = "Connect to a netconf device.")
public class NetconfConnectDeviceCommand extends AbstractAction {

    protected NetconfConsoleProvider service;

    public NetconfConnectDeviceCommand(NetconfConsoleProvider service) {
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

    @Option(name = "-U",
            aliases = { "--username" },
            description = "Username for netconf connection",
            required = true,
            multiValued = false)
    private String username;

    @Option(name = "-P",
            aliases = { "--password" },
            description = "Password for netconf connection",
            required = true,
            multiValued = false)
    private String password;

    @Option(name = "-t",
            aliases = { "--tcp-only" },
            description = "Type of connection, true for tcp only",
            required = false,
            multiValued = false)
    private String connectionType = "false";

    @Override
    protected Object doExecute() throws Exception {
        boolean isTcpOnly = (connectionType.equals("true")) ? true : false;
        boolean status = service.connectDevice(deviceIp, devicePort, username, password, isTcpOnly);
        String message = status ? "Netconf connector added succesfully" : "Failed to add netconf connector";
        return message;
    }
}
