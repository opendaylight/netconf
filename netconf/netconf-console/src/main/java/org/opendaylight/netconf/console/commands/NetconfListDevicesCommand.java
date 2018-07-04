/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.support.table.ShellTable;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;

@Command(name = "netconf:list-devices", scope = "netconf", description = "List all netconf devices in the topology.")
public class NetconfListDevicesCommand implements Action {

    protected final NetconfCommands service;

    public NetconfListDevicesCommand(final NetconfCommands service) {
        this.service = service;
    }

    @Override
    public Object execute() {
        final Map<String, Map<String, String>> allDevices = service.listDevices();
        printDevicesList(allDevices);
        return null;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private static void printDevicesList(@Nonnull final Map<String, Map<String, String>> allDevices) {
        final ShellTable table = new ShellTable();
        table.column(NetconfConsoleConstants.NETCONF_ID).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_IP).alignLeft();
        table.column(NetconfConsoleConstants.NETCONF_PORT).alignLeft();
        table.column(NetconfConsoleConstants.STATUS).alignLeft();

        for (final Map<String, String> attributes : allDevices.values()) {
            table.addRow().addContent(attributes.get(NetconfConsoleConstants.NETCONF_ID),
                    attributes.get(NetconfConsoleConstants.NETCONF_IP),
                    attributes.get(NetconfConsoleConstants.NETCONF_PORT),
                    attributes.get(NetconfConsoleConstants.STATUS));
        }
        table.print(System.out);
    }
}
