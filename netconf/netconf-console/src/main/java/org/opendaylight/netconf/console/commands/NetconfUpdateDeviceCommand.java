/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@Command(name = "netconf:update-device", scope = "netconf", description = "Update netconf device attributes.")
public class NetconfUpdateDeviceCommand implements Action {

    protected final NetconfCommands service;

    public NetconfUpdateDeviceCommand(final NetconfCommands service) {
        this.service = service;
    }

    @VisibleForTesting
    NetconfUpdateDeviceCommand(final NetconfCommands service, final String newIp) {
        this.service = service;
        this.newIp = newIp;
    }

    @Option(name = "-id",
            aliases = { "--nodeId" },
            description = "NETCONF node ID of the netconf device",
            required = true,
            multiValued = false)
    private String deviceId;

    @Option(name = "-U",
            aliases = { "--username" },
            description = "Username for NETCONF connection",
            required = true,
            multiValued = false)
    private String username;

    @Option(name = "-P",
            aliases = { "--password" },
            description = "Password for NETCONF connection",
            required = true,
            multiValued = false)
    private String password;

    @Option(name = "-ni",
            aliases = { "--new-ipaddress" },
            description = "New IP address of NETCONF device",
            required = false,
            multiValued = false)
    private String newIp;

    @Option(name = "-np",
            aliases = { "--new-port" },
            description = "New Port of NETCONF device",
            required = false,
            multiValued = false)
    private String newPort;

    @Option(name = "-nU",
            aliases = { "--new-username" },
            description = "New Username for NETCONF connection",
            required = false,
            multiValued = false)
    private String newUsername;

    @Option(name = "-nP",
            aliases = { "--new-password" },
            description = "New Password for NETCONF connection",
            required = false,
            multiValued = false)
    private String newPassword;

    @Option(name = "-t",
            aliases = { "--tcp-only" },
            description = "Type of connection, true for tcp only",
            required = false,
            multiValued = false)
    private String newConnectionType = "false";

    @Option(name = "-sl",
            aliases = { "--schemaless" },
            description = "Schemaless surpport, true for schemaless",
            required = false,
            multiValued = false)
    private String newSchemaless = "false";

    @Override
    public  Object execute() {

        Map<String, String> updated = new HashMap<>();
        updated.put(NetconfConsoleConstants.NETCONF_IP, newIp);
        updated.put(NetconfConsoleConstants.NETCONF_PORT, newPort);
        updated.put(NetconfConsoleConstants.USERNAME, newUsername);
        updated.put(NetconfConsoleConstants.PASSWORD, newPassword);
        updated.put(NetconfConsoleConstants.TCP_ONLY, newConnectionType);
        updated.put(NetconfConsoleConstants.SCHEMALESS,newSchemaless);
        updated.values().remove(null);

        if (updated.isEmpty()) {
            return "Nothing to update.";
        }

        return service.updateDevice(deviceId, username, password, updated);
    }
}
