/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.commands;

import com.google.common.annotations.VisibleForTesting;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@Service
@Command(name = "list-devices", scope = "netconf", description = "List all netconf devices in the topology.")
public class NetconfListDevicesCommand implements Action {
    @Reference
    private NetconfCommands service;

    public NetconfListDevicesCommand() {
        // Nothing here, uses injection
    }

    @VisibleForTesting
    NetconfListDevicesCommand(final NetconfCommands service) {
        this.service = service;
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public Object execute() {
        final var table = new ShellTable();
        table.column(NetconfConsoleConstants.NETCONF_ID).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_IP).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_PORT).alignLeft();
        table.column(NetconfConsoleConstants.STATUS).alignLeft();

        for (var attributes : service.listDevices().values()) {
            table.addRow().addContent(
                attributes.get(NetconfConsoleConstants.NETCONF_ID),
                attributes.get(NetconfConsoleConstants.NETCONF_IP),
                attributes.get(NetconfConsoleConstants.NETCONF_PORT),
                attributes.get(NetconfConsoleConstants.STATUS));
        }
        table.print(System.out);
        return null;
    }
}
