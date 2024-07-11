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
import java.util.HashMap;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@Service
@Command(name = "update-device", scope = "netconf", description = "Update netconf device attributes.")
public class NetconfUpdateDeviceCommand implements Action {
    @Reference
    private NetconfCommands service;

    @Option(name = "-id",
            aliases = { "--nodeId" },
            description = "NETCONF node ID of the netconf device",
            required = true,
            multiValued = false)
    String deviceId;

    @Option(name = "-U",
            aliases = { "--username" },
            description = "Username for NETCONF connection",
            required = true,
            censor = true,
            multiValued = false)
    String username;

    @Option(name = "-P",
            aliases = { "--password" },
            description = "Password for NETCONF connection",
            required = true,
            censor = true,
            multiValued = false)
    String password;

    @Option(name = "-ni",
            aliases = { "--new-ipaddress" },
            description = "New IP address of NETCONF device",
            required = false,
            multiValued = false)
    String newIp;

    @Option(name = "-np",
            aliases = { "--new-port" },
            description = "New Port of NETCONF device",
            required = false,
            multiValued = false)
    String newPort;

    @Option(name = "-nU",
            aliases = { "--new-username" },
            description = "New Username for NETCONF connection",
            required = false,
            multiValued = false)
    String newUsername;

    @Option(name = "-nP",
            aliases = { "--new-password" },
            description = "New Password for NETCONF connection",
            required = false,
            multiValued = false)
    String newPassword;

    @Option(name = "-t",
            aliases = { "--tcp-only" },
            description = "Type of connection, true for tcp only",
            required = false,
            multiValued = false)
    String newConnectionType = "false";

    @Option(name = "-sl",
            aliases = { "--schemaless" },
            description = "Schemaless support, true for schemaless",
            required = false,
            multiValued = false)
    String newSchemaless = "false";

    public NetconfUpdateDeviceCommand() {
        // Nothing here, uses injection
    }

    @VisibleForTesting
    NetconfUpdateDeviceCommand(final NetconfCommands service, final String newIp) {
        this.service = requireNonNull(service);
        this.newIp = requireNonNull(newIp);
    }

    @Override
    public Object execute() {
        final var updated = new HashMap<String, String>();
        updated.put(NetconfConsoleConstants.NETCONF_IP, newIp);
        updated.put(NetconfConsoleConstants.NETCONF_PORT, newPort);
        updated.put(NetconfConsoleConstants.USERNAME, newUsername);
        updated.put(NetconfConsoleConstants.PASSWORD, newPassword);
        updated.put(NetconfConsoleConstants.TCP_ONLY, newConnectionType);
        updated.put(NetconfConsoleConstants.SCHEMALESS, newSchemaless);
        // FIXME: what is the intent here?
        updated.values().remove(null);

        if (updated.isEmpty()) {
            return "Nothing to update.";
        }

        return service.updateDevice(deviceId, username, password, updated);
    }
}
