/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.Map;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;
import org.apache.karaf.shell.table.ShellTable;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:list-devices", scope = "netconf", description = "List all netconf devices in the topology.")
public class NetconfListDevicesCommand extends AbstractAction {

    private static final String STATUS = "status";
    private static final String NETCONF_PORT = "netconf Port";
    private static final String NETCONF_IP = "netconf Ip";
    private static final String NETCONF_ID = "netconf Id";

    protected NetconfConsoleProvider service;

    public NetconfListDevicesCommand(NetconfConsoleProvider service) {
        this.service = service;
    }

    @Override
    protected Object doExecute() throws Exception {
        Map<String, Map<String, String>> allDevices = service.listDevices();
        printDevicesList(allDevices);
        return null;
    }

    private void printDevicesList(Map<String, Map<String, String>> allDevices) {
        ShellTable table = new ShellTable();
        table.column(NETCONF_ID).alignCenter();
        table.column(NETCONF_IP).alignCenter();
        table.column(NETCONF_PORT).alignCenter();
        table.column(STATUS).alignCenter();

        for (String nodeIds : allDevices.keySet()) {
            table.addRow().addContent(allDevices.get(nodeIds).get(NETCONF_ID),
                    allDevices.get(nodeIds).get(NETCONF_IP),
                    allDevices.get(nodeIds).get(NETCONF_PORT),
                    allDevices.get(nodeIds).get(STATUS));
        }
        table.print(System.out);
    }
}
